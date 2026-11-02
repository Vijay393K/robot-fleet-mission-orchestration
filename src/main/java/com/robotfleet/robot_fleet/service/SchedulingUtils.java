
package com.robotfleet.robot_fleet.service;

public class SchedulingUtils {

   
    public static int getPriorityRank(String priority) {

        return switch (priority.toUpperCase()) {
            case "CRITICAL" -> 1;
            case "HIGH" -> 2;
            case "MEDIUM" -> 3;
            case "LOW" -> 4;
            default -> 5;
        };
    }


    
    
    public static double calculateDistance(
            double robotX,
            double robotY,
            double pickupX,
            double pickupY) {

        double xDifference = robotX - pickupX;
        double yDifference = robotY - pickupY;

        return Math.sqrt(
                (xDifference * xDifference)
                + (yDifference * yDifference)
        );
    }
}
