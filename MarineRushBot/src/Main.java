import bwapi.*;
import bwapi.Text.Size.Enum;
import bwta.BWTA;
import bwta.BaseLocation;
import module.Economy;

import java.util.Collection;
import java.util.List;

public class Main extends DefaultBWListener {

    private Mirror mirror = new Mirror();

    private Game game;

    private Player self;

    private Economy economy;

    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
        if (unit.getType().isWorker() && unit.isIdle()) {
            dispatchWorker(unit);
        }
    }

    @Override
    public void onStart() {
        game = mirror.getGame();
        self = game.self();
        economy = new Economy(game, self);
        allin = false;

        //Use BWTA to analyze map
        //This may take a few minutes if the map is processed first time!
        System.out.println("Analyzing map...");
        BWTA.readMap();
        BWTA.analyze();
        System.out.println("Map data ready");

        int i = 0;
        for (BaseLocation baseLocation : BWTA.getBaseLocations()) {
            System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
            for (Position position : baseLocation.getRegion().getPolygon().getPoints()) {
                System.out.print(position + ", ");
            }
            System.out.println();
        }
    }

    @Override
    public void onFrame() {
        game.setTextSize(Enum.Small);
        game.drawTextScreen(10, 10, "Playing as " + self.getName() + " - " + self.getRace());
        economy.update();

        //iterate through my units
        List<Unit> units = self.getUnits();
        int expectedSupply = self.supplyTotal();

        StringBuilder sb = new StringBuilder();
        for (Unit myUnit : units) {
            if (self.minerals() >= 50 && (self.supplyTotal() > self.supplyUsed())) {
                //if there's enough minerals, train an SCV
                if (myUnit.getType() == UnitType.Terran_Command_Center && !myUnit.isTraining()) {
                    Collection<Economy.Gathering> gatherings = economy.getBaseGathering().values();
                    Economy.Gathering gathering = gatherings.iterator().next();
                    if (gathering.requireMineralWorker()) {
                        myUnit.train(UnitType.Terran_SCV);
                    }
                }
            }

            //if it's a worker and it's idle, send it to the closest mineral patch
            if (myUnit.getType().isWorker()) {
                if (myUnit.isConstructing()) {
                    UnitType target = myUnit.getBuildType();
                    if (target != null) {
                        expectedSupply += target.supplyProvided();
                        sb.append("constructing ").append(target).append('\n');
                    }
                }
                if (myUnit.isIdle()) {
                    dispatchWorker(myUnit);
                }
            }
        }
        game.drawTextScreen(500, 10, sb.toString());

        int barrackCount = 0;

        for (Unit unit : units) {
            if (unit.getType() == UnitType.Terran_Barracks) {
                barrackCount++;
            }
        }
        if (self.minerals() >= 50 && (self.supplyTotal() - self.supplyUsed()) > 0) {
            for (Unit unit : units) {
                if (unit.getType() == UnitType.Terran_Barracks && !unit.isTraining()) {
                    unit.train(UnitType.Terran_Marine);
                }
            }
        }

        if (self.supplyTotal() == 400 || (expectedSupply - self.supplyUsed() <= 2 + barrackCount * 4) && (self.minerals() >= 100)) {
            game.drawTextScreen(10, 25, "trying to build supply");
            //iterate over units to find a worker
            for (Unit myUnit : units) {
                if (myUnit.getType() == UnitType.Terran_SCV) {
                    if (myUnit.isConstructing()) continue;
                    //get a nice place to build a supply depot
                    TilePosition buildTile = getBuildTile(myUnit, UnitType.Terran_Supply_Depot, self.getStartLocation());
                    //and, if found, send the worker to build it (and leave others alone - break;)
                    if (buildTile != null) {
                        myUnit.build(UnitType.Terran_Supply_Depot, buildTile);
                    }
                    break;
                }
            }
        } else if (self.minerals() >= 150 && barrackCount < 6 && (self.supplyTotal() > self.supplyUsed())) {
            //iterate over units to find a worker
            for (Unit myUnit : units) {
                if (barrackCount < 8) {
                    if (myUnit.getType() == UnitType.Terran_SCV) {
                        if (myUnit.isConstructing()) break;
                        //get a nice place to build a supply depot
                        TilePosition buildTile = getBuildTile(myUnit, UnitType.Terran_Barracks, self.getStartLocation());
                        //and, if found, send the worker to build it (and leave others alone - break;)
                        if (buildTile != null) {
                            myUnit.build(UnitType.Terran_Barracks, buildTile);
                        }
                        break;
                    }
                }
            }
        }

        if (game.getFrameCount() % 240 == 2) {
            if (self.supplyUsed() > 200 || allin) {
                allin = true;
                BaseLocation enemy = getEnemyBase();
                if (enemy != null) {
                    for (Unit myUnit : units) {
                        if (myUnit.getType() == UnitType.Terran_Marine) {
                            tryAttack(myUnit, enemy.getPosition());
                        }
                    }
                }
            } else if (self.supplyUsed() > 150) {
                BaseLocation enemy = getEnemyBase();
                if (enemy != null) {
                    BaseLocation secondBase = getSecondBase(enemy.getTilePosition());
                    for (Unit myUnit : units) {
                        if (myUnit.getType() == UnitType.Terran_Marine) {
                            tryAttack(myUnit, secondBase.getPosition());
                        }
                    }
                }
            }
            if (self.supplyUsed() < 150) {
                allin = false;
                BaseLocation secondBase = getSecondBase(self.getStartLocation());
                for (Unit myUnit : units) {
                    if (myUnit.getType() == UnitType.Terran_Marine) {
                        tryAttack(myUnit, secondBase.getPosition());
                    }
                }
            }
        }
    }
    private boolean allin = false;

    private void tryAttack(Unit unit, Position target) {
        if (unit.isIdle()) {
            unit.attack(target);
        } else {
            Position orderTargetPosition = unit.getOrderTargetPosition();
            if (orderTargetPosition == null || orderTargetPosition.getDistance(target) > 300) {
                unit.attack(target);
            }
        }
    }

    private BaseLocation getEnemyBase() {
        TilePosition startLocation = self.getStartLocation();
        for (BaseLocation b : BWTA.getBaseLocations()) {
            if (b.isStartLocation()) {
                TilePosition enemy = b.getTilePosition();
                if (enemy.getX() != startLocation.getX() && enemy.getY() != startLocation.getY()) {
                    return b;
                }
            }
        }
        return null;
    }
    private BaseLocation getSecondBase(TilePosition startLocation) {
        BaseLocation sec = null;
        double min = Double.MAX_VALUE;
        for (BaseLocation b : BWTA.getBaseLocations()) {
            TilePosition base = b.getTilePosition();
            if (base.getX() != startLocation.getX() && base.getY() != startLocation.getY()) {
                double d = startLocation.getDistance(base);
                if (d < min) {
                    min = d;
                    sec = b;
                }
            }
        }
        return sec;
    }

    private void dispatchWorker(Unit myUnit) {
        Unit closestMineral = null;

        //find the closest mineral
        for (Unit neutralUnit : game.neutral().getUnits()) {
            if (neutralUnit.getType().isMineralField()) {
                if (closestMineral == null || myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral)) {
                    closestMineral = neutralUnit;
                }
            }
        }

        //if a mineral patch was found, send the worker to gather it
        if (closestMineral != null) {
            myUnit.gather(closestMineral, false);
        }
    }

    public static void main(String[] args) {
        new Main().run();
    }

    // Returns a suitable TilePosition to build a given building type near
// specified TilePosition aroundTile, or null if not found. (builder parameter is our worker)
    public TilePosition getBuildTile(Unit builder, UnitType buildingType, TilePosition aroundTile) {
        TilePosition ret = null;
        int maxDist = 3;
        int stopDist = 40;

        // Refinery, Assimilator, Extractor
        if (buildingType.isRefinery()) {
            for (Unit n : game.neutral().getUnits()) {
                if ((n.getType() == UnitType.Resource_Vespene_Geyser) &&
                    (Math.abs(n.getTilePosition().getX() - aroundTile.getX()) < stopDist) &&
                    (Math.abs(n.getTilePosition().getY() - aroundTile.getY()) < stopDist)
                        ) return n.getTilePosition();
            }
        }

        while ((maxDist < stopDist) && (ret == null)) {
            for (int i = aroundTile.getX() - maxDist; i <= aroundTile.getX() + maxDist; i++) {
                for (int j = aroundTile.getY() - maxDist; j <= aroundTile.getY() + maxDist; j++) {
                    if (game.canBuildHere(new TilePosition(i, j), buildingType, builder, false)) {
                        // units that are blocking the tile
                        boolean unitsInWay = false;
                        for (Unit u : game.getAllUnits()) {
                            if (u.getID() == builder.getID()) continue;
                            if ((Math.abs(u.getTilePosition().getX() - i) < 4) && (Math.abs(u.getTilePosition().getY() - j) < 4)) unitsInWay = true;
                        }
                        if (!unitsInWay) {
                            return new TilePosition(i, j);
                        }
                        // creep for Zerg
                        if (buildingType.requiresCreep()) {
                            boolean creepMissing = false;
                            for (int k = i; k <= i + buildingType.tileWidth(); k++) {
                                for (int l = j; l <= j + buildingType.tileHeight(); l++) {
                                    if (!game.hasCreep(k, l)) creepMissing = true;
                                    break;
                                }
                            }
                            if (creepMissing) continue;
                        }
                    }
                }
            }
            maxDist += 2;
        }

        if (ret == null) game.printf("Unable to find suitable build position for " + buildingType.toString());
        return ret;
    }
}