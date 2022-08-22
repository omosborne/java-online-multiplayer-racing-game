package game.client;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.util.*;

/**
 * The {@code Kart} class controls updating and processing
 * of kart information.
 */
public class Kart {

    // Constants.
    private static final float SPEED_MAX    = 2f;
    private static final int SPEED_MIN      = 0;
    private static final float ACCELERATION = 0.1f;
    private static final float TURN_SPEED   = 1.5f;
    private static final int TURN_CIRCLE    = 160;
    private static final int HIT_BOX_BUFFER = 15;
    private static final float SLOW_RATE    = 0.025f;
    private static final int BOTTOM         = 0;
    private static final int RIGHT          = 1;
    private static final int TOP            = 2;
    private static final int LEFT           = 3;

    // Image sets.
    private final ImageIcon[] kartSprites = new ImageIcon[16];

    // Object properties.
    private final int kartNumber;
    private int direction;
    private float rotation;
    private float speed;
    private final Point2D.Float position = new Point2D.Float();
    private int kartType;
    private ImageIcon image;
    private final Player owner;
    private final Rectangle hitBox;
    private final Racetrack racetrack;
    private final Area track;
    private boolean kartCrashed;

    // Property access methods.
    public Player getOwner()            { return owner; }
    public float getSpeed()             { return speed; }
    public float getRotation()          { return rotation; }
    public int getKartNumber()          { return kartNumber; }
    public ImageIcon getImage()         { return image; }
    public Rectangle getHitBox()        { return hitBox; }
    public Point2D.Float getPosition()  { return position; }
    public boolean hasCrashed()         { return kartCrashed; }

    public void setRotation(float newRotation) {
        rotation = newRotation;
    }

    public void setSpeed(float newSpeed) {
        speed = newSpeed;
    }

    public void setPosition(float x, float y) {
        position.x = x;
        position.y = y;
    }

    public boolean isMoving() {
        return speed != SPEED_MIN;
    }

    public void reduceSpeed() {
        if (speed < SPEED_MIN - SLOW_RATE) speed += SLOW_RATE;
        else if (speed > SPEED_MIN + SLOW_RATE) speed -= SLOW_RATE;
        else speed = SPEED_MIN;
    }

    // Constructor.
    public Kart(int startDirection, Point startPosition, Player owner, int kartType, Racetrack racetrack) {
        this.racetrack = racetrack;
        track = racetrack.getPlayableArea();
        this.kartType = kartType;

        loadImages();

        // Allocate kart information.
        this.owner  = owner;
        kartNumber  = owner.getPlayerNumber();
        direction   = startDirection;
        rotation    = (float) direction * 10;
        image       = kartSprites[direction];
        position.setLocation(startPosition);

        // Create a forgiving kart bounds area that is smaller than the
        // image for more accurate collision detection and leniency.
        hitBox = new Rectangle((int) (position.x + HIT_BOX_BUFFER), (int) (position.y + HIT_BOX_BUFFER),
                image.getIconWidth() - HIT_BOX_BUFFER * 2, image.getIconHeight() - HIT_BOX_BUFFER * 2);
    }

    private void loadImages() {
        try {
            Arrays.setAll(kartSprites, i -> new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("images/kart/style" + kartType + "/kart" + i + ".png"))));
        }
        catch (NullPointerException e) {
            System.err.println("Failed to locate a necessary image file.");
        }
    }

    public void updatePosition() {
        float newPositionX = position.x + getSpeedMultiplierX();
        float newPositionY = position.y + getSpeedMultiplierY();

        hitBox.setLocation((int) newPositionX + HIT_BOX_BUFFER,(int) newPositionY + HIT_BOX_BUFFER);

        // Collision detection with track boundaries.
        if (isNewPositionOnTrack()) {
            kartCrashed = false;
            position.setLocation(newPositionX, newPositionY);
        }
    }

    public boolean isNewPositionOnTrack() {
        if (track.contains(hitBox)) return true;

        kartCrashed = true;
        speed = -0.5f; // Bounce off the boundary.
        AudioManager.playSound("KART_COLLISION", false);
        return false;
    }

    private float getSpeedMultiplierX() {
        float multiplier = switch (direction) {
            case 2,3,4,5,6      ->  1;      // Fast movement right
            case 1,7            ->  0.5f;   // Slow movement right
            case 0,8            ->  0;      // No horizontal movement
            case 9,15           -> -0.5f;   // Slow movement left
            case 10,11,12,13,14 -> -1;      // Fast movement left
            default -> 0;
        };
        return multiplier * speed;
    }

    private float getSpeedMultiplierY() {
        float multiplier = switch (direction) {
            case 6,7,8,9,10     ->  1;      // Fast movement down
            case 5,11           ->  0.5f;   // Slow movement down
            case 4,12           ->  0;      // No vertical movement
            case 3,13           -> -0.5f;   // Slow movement up
            case 0,1,2,14,15    -> -1;      // Fast movement up
            default -> 0;
        };
        return multiplier * speed;
    }

    public void updateRotation(int rotationDirection) {
        final int right = 0;
        final int left = 1;

        // Modulus is used to ensure rotation loops back around from max to min.
        rotation = switch (rotationDirection) {
            case right -> (rotation + TURN_SPEED) % TURN_CIRCLE;
            case left -> (rotation + (TURN_CIRCLE - TURN_SPEED)) % TURN_CIRCLE;
            default -> throw new IllegalStateException("Unrecognised direction: " + rotationDirection);
        };
        updateImage();
    }

    public void updateImage() {
        direction = (int) rotation / 10;
        image = kartSprites[direction];
    }

    public void updateSpeed(int speedDirection) {
        speed += speedDirection * ACCELERATION;
        // If the new speed breaches the bounds, return it back within.
        if (speed > SPEED_MAX) speed = SPEED_MAX;
        else if (speed < SPEED_MIN) speed = SPEED_MIN;
    }

    public boolean isGoingWrongWay() {
        if (isOnBottomTrack() && isFacingLeft()) return true;
        else if (isOnRightTrack() && isFacingDown()) return true;
        else if (isOnTopTrack() && isFacingRight()) return true;
        else return isOnLeftTrack() && isFacingUp();
    }

    private boolean isFacingLeft() {
        return direction <= 15 && direction >= 9;
    }

    private boolean isFacingDown() {
        return direction <= 11 && direction >= 5;
    }

    private boolean isFacingRight() {
        return direction <= 7 && direction >= 1;
    }

    private boolean isFacingUp() {
        return (direction <= 3 && direction >= 0) || (direction <= 15 && direction >= 13);
    }

    private boolean isOnBottomTrack() {
        return position.y + HIT_BOX_BUFFER >= racetrack.getInnerBounds(BOTTOM).y;
    }

    private boolean isOnRightTrack() {
        return position.x + HIT_BOX_BUFFER >= racetrack.getInnerBounds(RIGHT).x;
    }

    private boolean isOnTopTrack() {
        return position.y - HIT_BOX_BUFFER <= racetrack.getInnerBounds(TOP).y;
    }

    private boolean isOnLeftTrack() {
        return position.x - HIT_BOX_BUFFER <= racetrack.getInnerBounds(LEFT).x;
    }
}
