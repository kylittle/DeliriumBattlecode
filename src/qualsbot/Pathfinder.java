package qualsbot;

import battlecode.common.*;

import java.util.ArrayList;

class MemoryQueue{
    private ArrayList<MapLocation> q;
    private int cap;

    public MemoryQueue(int s){
        if(s <= 0) throw new IllegalArgumentException("Must have memory > 0");
        q = new ArrayList<>();
        cap = s;
    }

    public void add(MapLocation m){
        // if we're full, delete old memory
        while(q.size() >= cap) {
            q.remove(0);
        }
        q.add(m);
    }

    public void forget(){
        q = new ArrayList<>();
    }

    public boolean contains(MapLocation m){
        return q.contains(m);
    }
}

/**
 * class for moving around the map
 */
public class Pathfinder {
    private RobotController rc;
	
	private static final int SPACE_MEMORY = 20;

    private Direction scoutDir;
	private MemoryQueue memory = new MemoryQueue(SPACE_MEMORY);
	private ArrayList<MapLocation> badSpaces = new ArrayList<>();

    static Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST
    };

    public Pathfinder(RobotController rc_param){
        rc = rc_param;
        scoutDir = randomDir();
    }

    public void addBadSpaces(ArrayList<MapLocation> spaces){
        for(MapLocation m : spaces){
            addBadSpace(m);
        }
    }

    public void addBadSpace(MapLocation m){
        if(badSpaces.contains(m)) return; // don't add the same space twice
        badSpaces.add(m);
    }

    public void forget(){
        memory.forget();
    }

    /**
     * returns whether the space immediately in the given direction is NOT flooded
     * @param type type of robot (here because sometimes we want to check for someone else)
     * @param dir direction to check in
     * @return true if type is drone or space is not flooded, false otherwise
     */
    public boolean sinkSafe(RobotType type, Direction dir) throws GameActionException{
        if(type == RobotType.DELIVERY_DRONE) return true;
        return !rc.senseFlooding(rc.adjacentLocation(dir));
    }

    /**
     * overload for readability
     */
    public boolean sinkSafe(Direction dir) throws GameActionException{
        return sinkSafe(rc.getType(), dir);
    }

    /**
     * attempts to move in the given direction
     * @param dir direction to move in
     * @return true if we move; false otherwise.
     */
    public boolean move(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMove(dir) && sinkSafe(dir) && !badSpaces.contains(rc.getLocation().add(dir))) {
            rc.move(dir);
            return true;
        } else return false;
    }

    /**
     * run in a straight direction; if we hit a wall, try a different direction.
     */
    public void scout() throws GameActionException {
        // try to scout in the same dir
        for(int i = 0; i < 8; i++){
            if(move(scoutDir)) return;
            scoutDir = scoutDir.rotateRight().rotateRight().rotateRight();
        }
        // we are trapped :c
    }

    private Direction[] directionsToTry(Direction dir){
        return new Direction[]{
                dir, dir.rotateLeft(), dir.rotateRight(),
                dir.rotateLeft().rotateLeft(), dir.rotateRight().rotateRight(),
                dir.rotateLeft().rotateLeft().rotateLeft(), dir.rotateRight().rotateRight().rotateRight(),
                dir.opposite()
        };
    }

    /**
     * pathfinding (currently lifted straight from lecturebot)
     * @param loc location to move to
     * @return true if we move, false otherwise
     */
    public boolean to(MapLocation loc) throws GameActionException {
        if(loc == null) return false;
        memory.add(rc.getLocation());
        Direction targetDir = rc.getLocation().directionTo(loc);
        for(Direction dir : directionsToTry(targetDir)){
            MapLocation result = rc.getLocation().add(dir);
            if(!memory.contains(result) && move(dir)) return true;
        }
        return false;
    }

    /**
     * @returns the direction to @param target subject to our movement restrictions
     */
    public Direction dirTo(MapLocation target) throws GameActionException {
        if(target == null) return null;
        Direction targetDir = rc.getLocation().directionTo(target);
        for(Direction dir : directionsToTry(targetDir)){
            MapLocation result = rc.getLocation().add(dir);
            if(!memory.contains(result) && !badSpaces.contains(result) && rc.canMove(dir)) return dir;
        }
        return null;
    }

//    /** DEPRECIATED
//     * pathfinding (currently lifted straight from lecturebot)
//     * @param dir direction to move towards
//     * @return true if we move, false otherwise
//     */
//    public boolean to(Direction dir) throws GameActionException{
//        if(dir == null) return false;
//        Direction[] toTry = {dir, dir.rotateLeft(), dir.rotateRight(),
//                dir.rotateLeft().rotateLeft(), dir.rotateRight().rotateRight()};
//        for (MapLocation prev : prev_loc){
//			for (Direction d : toTry){
//				if(!rc.getLocation().add(d).equals(prev)){
//					if(move(d)) {
//						prev_loc.add(rc.getLocation().add(d));
//						if(prev_loc.size() >= SPACE_MEMORY)
//							prev_loc.remove(0);
//						return true;
//					}
//				}
//			}
//        }
//        return false;
//    }

    private MapLocation[] allSpacesInRadius(){
        return allSpacesInRadius(rc.getCurrentSensorRadiusSquared());
    }

    /**
     * WARNING WARNING WARNING
     * THIS IS SLOW AF
     * TRY TO AVOID CALLING THIS EXCEPT WHEN TOTALLY NECESSARY
     * @param r radius to check
     * @return an array of every square in our radius
     */
    private MapLocation[] allSpacesInRadius(int r){
        r = (int)Math.sqrt(r);
        int max_x = rc.getLocation().x + r;
        int min_x = rc.getLocation().x - r;
        int max_y = rc.getLocation().y + r;
        int min_y = rc.getLocation().y - r;

        ArrayList<MapLocation> locs = new ArrayList<MapLocation>();

        // check all the squares in a box around us for if we can scan them
        // yes we waste a little time at the corners but it's an easy implementation
        for(int y = min_y; y <= max_y; y++){
            for(int x = min_x; x <= max_x; x++){
                MapLocation square = new MapLocation(x,y);
                if(!rc.getLocation().isWithinDistanceSquared(square, r)) continue;
                if(rc.canSenseLocation(square)) locs.add(square);
            }
        }

        return locs.toArray(new MapLocation[locs.size()]);
    }

    public MapLocation findSoup() throws GameActionException{
        for(MapLocation square : allSpacesInRadius()){
            if(rc.senseSoup(square) > 0) return square;
        }
        return null;
    }

    /**
     * @param l location to check legality of
     * @return true if legal, false otherwise
     */
    public boolean isLegal(MapLocation l){
        int x = l.x;
        int y = l.y;
        int mx = rc.getMapWidth()-1; // max x
        int my = rc.getMapHeight()-1; // max y
        return x <= mx && y <= my && x >= 0 && y >= 0;
    }

    public ArrayList<MapLocation> offsetsToLocations(int[][] offsets, MapLocation center){
        if(center == null) return null; // can't do anything without relative location
        ArrayList<MapLocation> buildPath = new ArrayList<>();
        int cx = center.x; // center x
        int cy = center.y; // center y
        for(int[] offset : offsets){
            int x = cx + offset[0];
            int y = cy + offset[1];
            MapLocation location = new MapLocation(x,y);
            if(!isLegal(location)) continue; // throw out impossible spaces
            buildPath.add(location);
        }
        return buildPath;
    }

    /**
     * work on creating a structure collaboratively
     * @param structure spaces that need to be filled, in order of what needs to be filled first
     * @param fallback location to path to if all the spaces are filled
     * @return true if we become part of the structure, false otherwise
     */
    public boolean assimilate(ArrayList<MapLocation> structure, MapLocation fallback) throws GameActionException {
        MapLocation targetPost = fallback;
        while(structure.size() > 0){
            MapLocation post = structure.get(0);

            // if we don't know a post is ok OR if we know it's ok, go to it
            if(!rc.canSenseLocation(post) || !rc.isLocationOccupied(post)) {
                targetPost = post;
                break;
            }
            // that post is occupied by a friendly unit? it's all good, forget about it.
            RobotInfo occupier = rc.senseRobotAtLocation(post);
            boolean occupier_friendly = occupier.getTeam().equals(rc.getTeam());
            boolean occupier_type = occupier.getType().equals(rc.getType());
            if(occupier_friendly && occupier_type) structure.remove(post);
        }
        System.out.println("pathing to " + targetPost);
        this.to(targetPost);
        if(rc.getLocation().equals(targetPost)) {
            return true; // we are in the right place!
        }
        return false;
    }

    public boolean awayFrom(MapLocation ref, int distancesq) throws GameActionException{
        int currentDistance = rc.getLocation().distanceSquaredTo(ref);
        if(currentDistance >= distancesq) return true;
        int approxSpacesAway = (int)Math.sqrt(distancesq);
        Direction bestPath = rc.getLocation().directionTo(ref).opposite();
        for(int i = 0; i < 7; i++){
            MapLocation target = ref;
            for(int j = 0; j < approxSpacesAway; j++){
                target = target.add(bestPath);
            }
            if(isLegal(target)){
                System.out.println("pathing to " + target);
                this.to(target);
                return false;
            }
            bestPath = bestPath.rotateRight();
        }
        System.out.println("I can't find a path!");
        return false;
    }

    public boolean validIsland(MapLocation m){
        if(m == null) return false;
        int cx = m.x; // center x
        int cy = m.y; // center y
        for(int[] offset : LandscaperBot.ISLAND_OFFSETS){
            int x = cx + offset[0];
            int y = cy + offset[1];
            MapLocation location = new MapLocation(x,y);
            if(!isLegal(location)) return false;
        }
        return true;
    }

    public MapLocation findSpotForIsland(MapLocation ref, int distancesq) throws GameActionException {
        int currentDistance = rc.getLocation().distanceSquaredTo(ref);
        if(currentDistance >= distancesq && validIsland(rc.getLocation())) return rc.getLocation();
        int approxSpacesAway = (int)Math.sqrt(distancesq);
        Direction bestPath = rc.getLocation().directionTo(ref).opposite();
        for(int i = 0; i < 7; i++){
            MapLocation target = ref;
            for(int j = 0; j < approxSpacesAway; j++){
                target = target.add(bestPath);
            }
            if(validIsland(target)){
                System.out.println("" + target + " looks like a good spot for an island");
                return target;
            }
            bestPath = bestPath.rotateRight();
        }
        System.out.println("ALL IS LOST");
        return null;
    }

    public Direction randomDir(){
        return directions[(int) (Math.random() * directions.length)];
    }

    /* * * * * * * * * * * * * * * * * * * * * * * * * *
     * SHARED METHODS BELOW                            *
     * * * * * * * * * * * * * * * * * * * * * * * * * */

    /**
     * tries to build a robot in the given direction
     *
     * @param type what to build
     * @param dir  where to build it
     * @return true if we successfully built it, false otherwise
     */
    public boolean build(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir) && sinkSafe(type, dir)) {
            rc.buildRobot(type, dir);
            return true;
        } else return false;
    }

    /**
     * tries to mine soup in the given direction
     *
     * @param dir where to mine
     * @return true if we mined it, false otherwise
     */
    public boolean mine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMineSoup(dir)) {
            rc.mineSoup(dir);
            return true;
        } else return false;
    }

    /**
     * tries to pick up a robot
     *
     * @param target robot to grab
     * @return true if we grab it, false otherwise
     */
    public boolean grab(RobotInfo target) throws GameActionException {
        if (rc.isReady() && rc.canPickUpUnit(target.getID())) {
            rc.pickUpUnit(target.getID());
            return true;
        } else return false;
    }

    /**
     * tries to drop a robot in the given direction
     *
     * @param dir where we droppin
     * @return true if dropped, false otherwise
     */
    public boolean drop(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDropUnit(dir)) {
            rc.dropUnit(dir);
            return true;
        } else return false;
    }

    public boolean drop(MapLocation m) throws GameActionException {
        return rc.getLocation().isAdjacentTo(m) && drop(rc.getLocation().directionTo(m));
    }

    /**
     * tries to refine soup in the given dir
     *
     * @param dir where to refine
     * @return true if we refined, false otherwise
     */
    public boolean refine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositSoup(dir)) {
            rc.depositSoup(dir, rc.getSoupCarrying());
            return true;
        } else return false;
    }

    /**
     * tries to shoot a unit
     *
     * @param target who to shoot
     * @return true if we shot them, false otherwise
     */
    public boolean shoot(RobotInfo target) throws GameActionException {
        if (rc.isReady() && rc.canShootUnit(target.getID())) {
            rc.shootUnit(target.getID());
            return true;
        } else return false;
    }

    /**
     * tries to dig in a direction
     *
     * @param dir where to dig
     * @return true if we dig, false otherwise
     */
    public boolean dig(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDigDirt(dir)) {
            rc.digDirt(dir);
            return true;
        } else return false;
    }

    /**
     * overload to change location to dir
     */
    public boolean dig(MapLocation m) throws GameActionException{
        return rc.getLocation().isAdjacentTo(m) && dig(rc.getLocation().directionTo(m));
    }

    /**
     * tries to dump in a direction
     * @param dir where to dump
     * @return true if we dump, false otherwise
     */
    public boolean dump(Direction dir) throws GameActionException{
        if(rc.isReady() && rc.canDepositDirt(dir)){
            rc.depositDirt(dir);
            return true;
        } else return false;
    }

    /**
     * overload to change location to dir
     */
    public boolean dump(MapLocation m) throws GameActionException{
        return rc.getLocation().isAdjacentTo(m) && dump(rc.getLocation().directionTo(m));
    }
}
