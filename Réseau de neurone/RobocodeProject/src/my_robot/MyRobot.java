/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package my_robot;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import robocode.*;
import robocode.util.Utils;

/**
 *
 * @author Dante
 */
public class MyRobot extends AdvancedRobot {

    public static final int BINS = 47;
    private Point2D.Double myLocation;//la position actuel de notre bot
    private Point2D.Double enemyLocation;//la derniere position connu du bot ennemie
    private ArrayList enemyWaves;//historiques des vagues enemie
    private ArrayList surfDirections;//liste de nos directions par rapport a l'ennemi on mets 1 pour le sens d'une aiguille et -1 sinon
    private ArrayList surfAbsBearings;//liste des angle (absoluteBearing) entre notre bot et le bot ennemies
    public static double surfStats[] = new double[BINS];
    public static double enemyEnergy = 100.0;
    //Terrain dans lequel on manipulera le robot (on se base sur une arene de taille 800*600)
    public final static Rectangle2D.Double fieldRect = new Rectangle2D.Double(18, 18, 764, 564);
    public final static double WALL_STICK = 160;

    @Override
    public void run() {
        enemyWaves = new ArrayList();
        surfDirections = new ArrayList<Integer>();
        surfAbsBearings = new ArrayList<Double>();
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        do {
            //on recherche constament l'ennemi
            turnRadarRightRadians(Double.POSITIVE_INFINITY);
        } while (true);
    }

    /**
     * si ennemy detecté !
     *
     * @param e
     */
    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        // setTurnGunRight(getRadarHeading());
        myLocation = new Point2D.Double(getX(), getY());
        //on recupere notre vitesse lateral
        double lateralVelocity = getVelocity() * Math.sin(e.getBearingRadians());
        double absBearing = e.getBearingRadians() + getHeadingRadians();

        setTurnRadarRightRadians(Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians()) * 2);

        surfDirections.add(0, new Integer((lateralVelocity >= 0) ? 1 : -1));
        surfAbsBearings.add(0, new Double(absBearing + Math.PI));

        double bulletPower = enemyEnergy - e.getEnergy();//on determine la puissande de la balle a partir de la perte d'energie ennemie
        //au moment ou on determine la baisse d'energie on reviens dans notre 
        //historique de direction (deux tours plus tot)
        // pour verifier notre position qui lui a servit pour visé
        if (bulletPower < 3.01 && bulletPower > 0.09 && surfDirections.size() > 2) {
            EnemyWave ew = new EnemyWave();
            ew.fireTime = getTime() - 1; //on prenant en compte qu'il a viser il ya deux tour il a donc tirer le tour d'apres.
            ew.bulletVelocity = bulletVelocity(bulletPower);//on recuperer la vitesse actuel de la balle
            ew.distanceTraveled = bulletVelocity(bulletPower);//etant donne que un tours seulment c'est passer la distance parcouru equivaut a la vitesse
            ew.direction = ((Integer) surfDirections.get(2)).intValue();//on deduit la direction de la balle par rapport a notre direction deux tour plus tot 
            ew.directAngle = ((Double) surfAbsBearings.get(2)).doubleValue();//on determine l'angle de tire de la balle par rapport a l'angle de notre ennemie 2tour precedent
            ew.fireLocation = (Point2D.Double) enemyLocation.clone(); //on recupere l'emplacemet du tire par rapport a la derniere position connu de l'ennemie 
            //apres avoir remplis toute ces info on ajoute dans les vague ennemis
            enemyWaves.add(ew);
        }
        //on recuepre la nouvelle energie de notre ennemie
        enemyEnergy = e.getEnergy();

        //mise a jour de la position de l'ennemie 
        enemyLocation = project(myLocation, absBearing, e.getDistance());

        updateWaves();
        doSurfing();


    }
    //calcule de la vitesse d'une balle ,formule donner par le robowiki

    public static double bulletVelocity(double power) {
        return (20.0 - (3.0 * power));
    }
    //projection de sont robot par raport a notre position ,notre angle ,et la distance qu'on va parcourire.

    public static Point2D.Double project(Point2D.Double sourceLocation,
            double angle, double length) {
        return new Point2D.Double(sourceLocation.x + Math.sin(angle) * length,
                sourceLocation.y + Math.cos(angle) * length);
    }

    /**
     * on mets a jours la liste des vague ennemis en retirant les vague qui nous
     * on depassé
     */
    public void updateWaves() {
        for (int x = 0; x < enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave) enemyWaves.get(x);
            ew.distanceTraveled = (getTime() - ew.fireTime) * ew.bulletVelocity;
            // si la  vague nous a deja depasser on la retire de la liste des vague ennemies a prendre en compte
            if (ew.distanceTraveled > myLocation.distance(ew.fireLocation) + 50) {
                enemyWaves.remove(x);
                x--;
            }
        }
    }

    /**
     * parcours la liste des vague ennemies a la recherche de la plus proche
     *
     * @return la vague la plus proche
     */
    private EnemyWave getClosestSurfableWave() {
        double closestDistance = 50000;
        EnemyWave surfWave = null;
        for (int x = 0; x < enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave) enemyWaves.get(x);
            double distance = myLocation.distance(ew.fireLocation) - ew.distanceTraveled;
            //si la vague ne nous a pas atteint et que c'est la plus proches
            if (distance > ew.bulletVelocity && distance < closestDistance) {
                surfWave = ew;
                closestDistance = distance;
            }
        }
        return surfWave;
    }

    private static int getFactorIndex(EnemyWave ew, Point2D.Double targetLocation) {
        double offsetAngle = (absoluteBearing(ew.fireLocation, targetLocation)
                - ew.directAngle);
        double factor = Utils.normalRelativeAngle(offsetAngle)
                / maxEscapeAngle(ew.bulletVelocity) * ew.direction;
        return (int) limit(0, (factor * ((BINS - 1) / 2)) + ((BINS - 1) / 2), BINS - 1);
    }

    /**
     * @param source
     * @param target
     * @return l'angle entre votre robot et le robot ennemie en radian
     *
     */
    public static double absoluteBearing(Point2D.Double source, Point2D.Double target) {
        return Math.atan2(target.x - source.x, target.y - source.y);
    }

    /**
     * @param min
     * @param value
     * @param max
     * @return return le max entre min et le min entre value et max
     */
    public static double limit(double min, double value, double max) {
        return Math.max(min, Math.min(value, max));
    }

    public static double maxEscapeAngle(double velocity) {
        return Math.asin(8.0 / velocity);
    }

    /**
     *
     * @param ew
     * @param targetLocation
     */
    private void logHit(EnemyWave ew, Point2D.Double targetLocation) {
        int index = getFactorIndex(ew, targetLocation);

        for (int x = 0; x < BINS; x++) {
            surfStats[x] += 1.0 / (Math.pow(index - x, 2) + 1);
        }
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        //NORMALEMENT C'est pas senser etre vide (sinon on  a merder !)
        if (!enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(e.getBullet().getX(), e.getBullet().getY());
            EnemyWave hitWave = null;
            // on verifie parmis nos vagues ennemies en cherchant laquel a pu nous toucher.
            for (int x = 0; x < enemyWaves.size(); x++) {
                EnemyWave ew = (EnemyWave) enemyWaves.get(x);
                if (Math.abs(ew.distanceTraveled - myLocation.distance(ew.fireLocation)) < 50
                        && Math.abs(bulletVelocity(e.getBullet().getPower()) - ew.bulletVelocity) < 0.001) {
                    hitWave = ew;
                    break;
                }
            }
            if (hitWave != null) {
                logHit(hitWave, hitBulletLocation);
                //on peut supprimer cette vaque
                enemyWaves.remove(enemyWaves.lastIndexOf(hitWave));
            }
        }
    }

    /**
     *
     * @param surfWave
     * @param direction
     * @return
     */
    private Point2D.Double predictPosition(EnemyWave surfWave, int direction) {
        Point2D.Double predictedPosition = (Point2D.Double) myLocation.clone();
        double predictedVelocity = getVelocity();
        double predictedHeading = getHeadingRadians();
        double maxTurning, moveAngle, moveDir;

        int counter = 0; // number of ticks in the future
        boolean intercepted = false;

        do {
            //calcule de l'angle de deplacement optimal  on evite le mur
            moveAngle =
                    wallSmoothing(predictedPosition, absoluteBearing(surfWave.fireLocation,
                    predictedPosition) + (direction * (Math.PI / 2)), direction)
                    - predictedHeading;
            moveDir = 1;
            //on verifie si le cos de notre angle est positif sinon on part dans l'autre sens du char
            if (Math.cos(moveAngle) < 0) {
                moveAngle += Math.PI;
                moveDir = -1;
            }
            //on normalise notre angle car on tournera jamais plus de 180 degres
            moveAngle = Utils.normalRelativeAngle(moveAngle);

            //regles de calcul de l'angle max que peut atteindre un bot en tourant en un tour (tick)
            maxTurning = Math.PI / 720d * (40d - 3d * Math.abs(predictedVelocity));
            //on calcul notre future angle avec notre angle actuel + l'angle calculé ou l'angle maximal
            predictedHeading = Utils.normalRelativeAngle(predictedHeading
                    + limit(-maxTurning, moveAngle, maxTurning));

            //si la vitesse et la direction sont d'un signe different on doit ralentir
            // sinon on va accelerer

            predictedVelocity +=
                    (predictedVelocity * moveDir < 0 ? 2 * moveDir : moveDir);
            predictedVelocity = limit(-8, predictedVelocity, 8);
            // on calcul la nouvelle position 'optimal'
            predictedPosition = project(predictedPosition, predictedHeading,
                    predictedVelocity);
            counter++;
            //si on a intercepter la vague on s'arrete
            if (predictedPosition.distance(surfWave.fireLocation)
                    < surfWave.distanceTraveled + (counter * surfWave.bulletVelocity)
                    + surfWave.bulletVelocity) {
                intercepted = true;
            }
            //sinon on continue pendant 500tour
        } while (!intercepted && counter < 500);

        return predictedPosition;
    }

    public double wallSmoothing(Point2D.Double botLocation, double angle, int orientation) {
        while (!fieldRect.contains(project(botLocation, angle, WALL_STICK))) {
            angle += orientation * 0.05;
        }
        return angle;
    }

    public double checkDanger(EnemyWave surfWave, int direction) {
        int index = getFactorIndex(surfWave,
                predictPosition(surfWave, direction));

        return surfStats[index];
    }

    public void doSurfing() {
        EnemyWave surfWave = getClosestSurfableWave();

        if (surfWave == null) {
            return;
        }

        double dangerLeft = checkDanger(surfWave, -1);
        double dangerRight = checkDanger(surfWave, 1);

        double goAngle = absoluteBearing(surfWave.fireLocation, myLocation);
        if (dangerLeft < dangerRight) {
            goAngle = wallSmoothing(myLocation, goAngle - (Math.PI / 2), -1);
        } else {
            goAngle = wallSmoothing(myLocation, goAngle + (Math.PI / 2), 1);
        }

        setBackAsFront(this, goAngle);
    }

    public static void setBackAsFront(AdvancedRobot robot, double goAngle) {
        double angle = Utils.normalRelativeAngle(goAngle - robot.getHeadingRadians());
        if (Math.abs(angle) > (Math.PI / 2)) {
            if (angle < 0) {
                robot.setTurnRightRadians(Math.PI + angle);
            } else {
                robot.setTurnLeftRadians(Math.PI - angle);
            }
            robot.setBack(100);
        } else {
            if (angle < 0) {
                robot.setTurnLeftRadians(-1 * angle);
            } else {
                robot.setTurnRightRadians(angle);
            }
            robot.setAhead(100);
        }
    }
}
