package module;

import bwapi.Game;
import bwapi.Player;
import bwapi.Unit;
import bwapi.UnitType;

import java.util.*;

/**
 * Created by rSkip on 2016/3/5.
 */
public class Economy {
    private final Game   game;
    private final Player self;
    public Economy(Game game, Player self) {
        this.game = game;
        this.self = self;
    }

    private HashMap<Unit, Gathering> baseGathering = new HashMap<>();
    public HashMap<Unit, Gathering> getBaseGathering() {
        return baseGathering;
    }

    public class Gathering {

        private static final float MINERAL_EFFICIENCY_1 = 0.046f;
        private static final float MINERAL_EFFICIENCY_2 = 0.025f;

        private Unit      base           = null;
        private Set<Unit> mineralWorkers = new HashSet<>();
        private Set<Unit> mineral        = new HashSet<>();
        private Set<Unit> gasWorkers     = new HashSet<>();
        private Set<Unit> gas            = new HashSet<>();

        private Gathering(Unit base) {
            this.base = base;
        }

        public boolean requireMineralWorker() {
            int w = mineralWorkers.size();
            int m = mineral.size();
            return w < m * 2;
        }
        public float expectedMPF() {
            int w = mineralWorkers.size();
            int m = mineral.size();
            if (w <= m) {
                return w * MINERAL_EFFICIENCY_1;
            } else if (w > m && w <= 2 * m) {
                return m * MINERAL_EFFICIENCY_1 + (w - m) * MINERAL_EFFICIENCY_2;
            } else {
                return m * (MINERAL_EFFICIENCY_1 + MINERAL_EFFICIENCY_2);
            }
        }

    }

    private Unit findNearestBase(List<Unit> bases, Unit unit) {
        Unit nearestBase = null;
        int distance = Integer.MAX_VALUE;
        for (Unit base : bases) {
            int d = base.getDistance(unit);
            if (d < distance) {
                nearestBase = base;
                distance = d;
            }
        }
        return nearestBase;
    }

    private void recheckBaseAndWorkers() {
        List<Unit> units = self.getUnits();

        List<Unit> bases = new ArrayList<>();
        baseGathering.clear();
        for (Unit unit : units) {
            if (unit.getType() == UnitType.Terran_Command_Center) {
                bases.add(unit);
                baseGathering.put(unit, new Gathering(unit));
            }
        }
        for (Unit unit : units) {
            if (unit.getType().isWorker()) {
                if (unit.isGatheringMinerals()) {
                    Unit base = findNearestBase(bases, unit);
                    if (base != null) {
                        baseGathering.get(base).mineralWorkers.add(unit);
                    }
                } else if (unit.isGatheringGas()) {
                    Unit base = findNearestBase(bases, unit);
                    if (base != null) {
                        baseGathering.get(base).gasWorkers.add(unit);
                    }
                }
            }
        }

        for (Unit neutralUnit : game.neutral().getUnits()) {
            Unit base = findNearestBase(bases, neutralUnit);
            if (base != null && base.getDistance(neutralUnit) < 300) {
                Gathering gathering = baseGathering.get(base);
                if (neutralUnit.getType().isMineralField()) {
                    gathering.mineral.add(neutralUnit);
                } else if (neutralUnit.getType() == UnitType.Resource_Vespene_Geyser) {
                    gathering.gas.add(neutralUnit);
                }
            }
        }
    }

    public void update() {
        if (game.getFrameCount() % 24 == 0) {
            recheckBaseAndWorkers();
        }
//        if (game.getFrameCount() % (24 * 60) == 0) {
//            System.out.printf("mineral count @ %d min : %d\n", game.getFrameCount() / 24 / 60, self.minerals());
//        }

        StringBuilder s = new StringBuilder();
        for (Gathering gathering : baseGathering.values()) {
            int mpm = (int) (gathering.expectedMPF() * 24 * 60);
            s.append(String.format("mpm = %d, train = %s\n", mpm, gathering.requireMineralWorker()));
        }
        game.drawTextScreen(10, 35, s.toString());
    }
}
