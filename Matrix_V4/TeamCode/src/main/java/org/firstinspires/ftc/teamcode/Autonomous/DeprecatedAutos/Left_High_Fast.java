package org.firstinspires.ftc.teamcode.Autonomous.DeprecatedAutos;

import static org.firstinspires.ftc.teamcode.TransferClass.poseStorage;
import static org.firstinspires.ftc.teamcode.TransferClass.turretAngle;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.acmerobotics.roadrunner.trajectory.Trajectory;
import com.acmerobotics.roadrunner.trajectory.constraints.TrajectoryAccelerationConstraint;
import com.outoftheboxrobotics.photoncore.PhotonCore;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.hardware.rev.RevColorSensorV3;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.AnalogInput;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.VoltageUnit;
import org.firstinspires.ftc.teamcode.Subsystems.Lift;
import org.firstinspires.ftc.teamcode.Subsystems.Sensors;
import org.firstinspires.ftc.teamcode.Subsystems.Servos;
import org.firstinspires.ftc.teamcode.Subsystems.Turret;
import org.firstinspires.ftc.teamcode.Vision.Pipelines.AprilTagDetectionPipeline;
import org.firstinspires.ftc.teamcode.drive.DriveConstants;
import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;
import org.firstinspires.ftc.teamcode.trajectorysequence.TrajectorySequence;
import org.openftc.apriltag.AprilTagDetection;
import org.openftc.easyopencv.OpenCvCamera;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;

import java.util.ArrayList;
import java.util.List;

/**
 * Autonomous class for Left 4+1 on the Left Center High Pole
 */
@Autonomous(name="Left 4+1 -> HIGHFAST", group = "Left Autos")
@Disabled
public class Left_High_Fast extends LinearOpMode {


    Lift lift = null;
    Servos servos = null;
    Turret turret = null;
    Sensors sensors = null;

    /**
     * The time spent in Autonomous
     */
    ElapsedTime AutoTime = new ElapsedTime();
    private final double totalTime = 30;
    private double timeLeft = 30;

    private boolean EmergencyParkFlag = false;

    final double MAX_SPEED_AUTO = DriveConstants.MAX_VEL;

    OpenCvCamera camera;
    AprilTagDetectionPipeline aprilTagDetectionPipeline;

    static final double FEET_PER_METER = 3.28084;

    // Lens intrinsics
    // UNITS ARE PIXELS
    // NOTE: this calibration is for the C920 webcam at 800x448.
    // You will need to do your own calibration for other configurations!
    double fx = 578.272;
    double fy = 578.272;
    double cx = 402.145;
    double cy = 221.506;

    // UNITS ARE METERS
    double tagsize = 0.166;

    int ID_TAG_OF_INTEREST = 18; // Tag ID 18 from the 36h11 family

    int PARKING_ZONE1 = 0, PARKING_ZONE2 = 1, PARKING_ZONE3 = 2;

    int[] MATRIX_IDS = {3, 7, 9};

    AprilTagDetection tagOfInterest = null;


    final Pose2d droppingPosition0 = new Pose2d(-39.2, -12.8, Math.toRadians(180));
    final Pose2d droppingPosition = new Pose2d(-39.2, -12.00, Math.toRadians(180));
    final Pose2d pickingPosition = new Pose2d(-49, -12, Math.toRadians(180));

    /**
     * {@inheritDoc}
     * @throws InterruptedException
     */
    @Override
    public void runOpMode() throws InterruptedException {
        PhotonCore.enable();

        List<LynxModule> allHubs = hardwareMap.getAll(LynxModule.class);

        if(allHubs.get(0).getInputVoltage(VoltageUnit.VOLTS) < 12.2){
            telemetry.addLine("******************WARNING: RELIABLITY ISSUES MAY BE EXPERIENCED IN AUTO DUE TO BATTERY******************");
            telemetry.update();
        }

        Pose2d PARKING1 = new Pose2d(-60, -12, Math.toRadians(180));
        Pose2d PARKING2 = new Pose2d(-36, -13, Math.toRadians(180));
        Pose2d PARKING3 = new Pose2d(-12, -12, Math.toRadians(180));
        int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        camera = OpenCvCameraFactory.getInstance().createWebcam(hardwareMap.get(WebcamName.class, "Webcam 1"), cameraMonitorViewId);
        aprilTagDetectionPipeline = new AprilTagDetectionPipeline(tagsize, fx, fy, cx, cy);

        camera.setPipeline(aprilTagDetectionPipeline);
        camera.openCameraDeviceAsync(new OpenCvCamera.AsyncCameraOpenListener() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void onOpened() {
                camera.startStreaming(1280, 720, OpenCvCameraRotation.UPRIGHT);
            }

            /**
             * {@inheritDoc}
             * @param errorCode
             */
            @Override
            public void onError(int errorCode) {

            }
        });
        telemetry.setMsTransmissionInterval(50);


        lift = new Lift(hardwareMap, telemetry);
        servos = new Servos(hardwareMap, telemetry);
        turret = new Turret(hardwareMap, "turret", telemetry);
        sensors = new Sensors(hardwareMap, telemetry);

        lift.reset();
        turret.reset();
        setInitialPositions();

        SampleMecanumDrive drive = new SampleMecanumDrive(hardwareMap, telemetry);
        Pose2d startPose = new Pose2d(-32, -63.3, Math.toRadians(180));
        drive.setPoseEstimate(startPose);

        /**
         * TrajectorySequence for moving from starting position to dropping preload cone on high pole
         */
        TrajectorySequence startToCenter = drive.trajectorySequenceBuilder(startPose)

                .addTemporalMarker(() -> lift.extendToLowPole())
                .addTemporalMarker(() -> Servos.Wrist.goTop())
                .addTemporalMarker(() -> turret.setDegree(-144))
                .addTemporalMarker(() -> lift.extendToHighPole())
                .addTemporalMarker(() -> Servos.Slider.moveOutside())
//                .addTemporalMarker(() -> Servos.AlignBar.outside())
                .lineToLinearHeading(droppingPosition0)
                .addTemporalMarker(()-> Servos.Slider.moveSlider(0.9))
                .waitSeconds(0.5)
                .addTemporalMarker(() -> Servos.Wrist.goGripping())
                .addTemporalMarker(() -> Servos.Gripper.setPosition(1))
//                .addTemporalMarker(() -> Servos.AlignBar.inside())
                .addTemporalMarker(()-> Servos.Slider.moveSlider(1))
                .waitSeconds(0.5)
                .build();


        /**
         * TrajectorySequence for picking up the topmost cone
         */
        TrajectorySequence pick1 = drive.trajectorySequenceBuilder(startToCenter.end())
                .addTemporalMarker(() -> lift.extendTo(lift.AUTO_POSITION[4], 1))
                .addTemporalMarker(() -> Servos.Slider.moveHalfway())
                .waitSeconds(0.1)
                .addTemporalMarker(() -> turret.setDegreeHighPower(-10))
                .waitSeconds(0.2)
                .lineToLinearHeading(pickingPosition)
//                .waitSeconds(0.5)
                .addTemporalMarker(() -> Servos.Slider.moveSlider(1))
                .addTemporalMarker(() -> turret.setDegree(0))
                .waitSeconds(0.5)
                .addTemporalMarker(() -> Servos.Gripper.closeGripper())
                .waitSeconds(0.5)
                .addTemporalMarker(() -> lift.extendToLowPole())
                .waitSeconds(0.2)
                .build();

        /**
         * General dropping sequence
         */
        TrajectorySequence dropCone1 = drive.trajectorySequenceBuilder(pick1.end())
                .addTemporalMarker(() -> turret.setDegree(-146))
//                .addTemporalMarker(()-> Servos.Slider.moveInside())
                .addTemporalMarker(() -> Servos.Wrist.goTop())
                .addTemporalMarker(() -> Servos.Slider.moveSlider(1))
                .lineToLinearHeading(droppingPosition)
//                .addTemporalMarker(()-> Servos.AlignBar.outside())
                .UNSTABLE_addTemporalMarkerOffset(-1, () -> {
                    lift.extendToHighPole();
                })
                .addTemporalMarker(()->Servos.AlignBar.outside())
                .waitSeconds(0.5)
                .addTemporalMarker(()-> Servos.Slider.moveSlider(0.7))
                .waitSeconds(1)
                .addTemporalMarker(() -> Servos.Wrist.goGripping())
                .waitSeconds(0.1)
                .addTemporalMarker(() -> Servos.Gripper.setPosition(1))
                .addTemporalMarker(() -> Servos.AlignBar.inside())
                .addTemporalMarker(()->Servos.Slider.moveOutside())
                .waitSeconds(0.1)
                .build();

        /**
         * TrajectorySequence for picking up the second cone
         */
        TrajectorySequence pick2 = drive.trajectorySequenceBuilder(dropCone1.end())
//                .addTemporalMarker(()-> Servos.Wrist.goGripping())
                .addTemporalMarker(() -> turret.setDegreeHighPower(-10))
                .addTemporalMarker(() -> lift.extendTo(lift.AUTO_POSITION[3], 1))
                .addTemporalMarker(() -> Servos.Slider.moveHalfway())
                .waitSeconds(0.1)
                .addTemporalMarker(() -> turret.setDegreeHighPower(-10))
                .waitSeconds(0.2)
                .lineToLinearHeading(pickingPosition)
//                .waitSeconds(0.5)
                .addTemporalMarker(() -> Servos.Slider.moveSlider(1))
                .addTemporalMarker(() -> turret.setDegree(0))
                .waitSeconds(0.5)
                .addTemporalMarker(() -> Servos.Gripper.closeGripper())
                .waitSeconds(0.5)
                .addTemporalMarker(() -> lift.extendToLowPole())
                .waitSeconds(0.2)
                .build();

        /**
         * TrajectorySequence for picking up the third cone
         */
        TrajectorySequence pick3 = drive.trajectorySequenceBuilder(dropCone1.end())
//                .addTemporalMarker(()-> Servos.Wrist.goGripping())
                .addTemporalMarker(() -> turret.setDegreeHighPower(-10))
                .addTemporalMarker(() -> lift.extendTo(lift.AUTO_POSITION[2], 1))
                .addTemporalMarker(() -> Servos.Slider.moveHalfway())
                .waitSeconds(0.1)
                .addTemporalMarker(() -> turret.setDegreeHighPower(-10))
                .waitSeconds(0.2)
                .lineToLinearHeading(pickingPosition)
//                .waitSeconds(0.5)
                .addTemporalMarker(() -> Servos.Slider.moveSlider(1))
                .addTemporalMarker(() -> turret.setDegree(0))
                .waitSeconds(0.5)
                .addTemporalMarker(() -> Servos.Gripper.closeGripper())
                .waitSeconds(0.5)
                .addTemporalMarker(() -> lift.extendToLowPole())
                .waitSeconds(0.2)
                .build();


        /**
         * TrajectorySequence for picking up the fourth cone
         */
        TrajectorySequence pick4 = drive.trajectorySequenceBuilder(dropCone1.end())
//                .addTemporalMarker(()-> Servos.Wrist.goGripping())
                .addTemporalMarker(() -> turret.setDegreeHighPower(-10))
                .addTemporalMarker(() -> lift.extendTo(lift.AUTO_POSITION[1], 1))
                .addTemporalMarker(() -> Servos.Slider.moveHalfway())
                .waitSeconds(0.1)
                .addTemporalMarker(() -> turret.setDegreeHighPower(-10))
                .waitSeconds(0.2)
                .lineToLinearHeading(pickingPosition)
//                .waitSeconds(0.5)
                .addTemporalMarker(() -> Servos.Slider.moveSlider(1))
                .addTemporalMarker(() -> turret.setDegree(0))
                .waitSeconds(0.5)
                .addTemporalMarker(() -> Servos.Gripper.closeGripper())
                .waitSeconds(0.5)
                .addTemporalMarker(() -> lift.extendToLowPole())
                .waitSeconds(0.2)
                .build();

        @Deprecated
        /**
         * TrajectorySequence for picking up the fifth cone
         */

                TrajectorySequence pick5 = drive.trajectorySequenceBuilder(dropCone1.end())
//                .addTemporalMarker(()-> Servos.Wrist.goGripping())
                .addTemporalMarker(() -> turret.setDegreeHighPower(-10))
                .addTemporalMarker(() -> lift.extendTo(lift.AUTO_POSITION[1], 1))
                .addTemporalMarker(() -> Servos.Slider.moveHalfway())
                .waitSeconds(0.1)
                .addTemporalMarker(() -> turret.setDegreeHighPower(-10))
                .waitSeconds(0.2)
                .lineToLinearHeading(pickingPosition)
//                .waitSeconds(0.5)
                .addTemporalMarker(() -> Servos.Slider.moveSlider(0.9))
                .addTemporalMarker(() -> turret.setDegree(0))
                .waitSeconds(0.5)
                .addTemporalMarker(() -> Servos.Gripper.closeGripper())
//                .waitSeconds(0.5)
//                .addTemporalMarker(()->lift.extendToLowPole())
//                .waitSeconds(0.2)
                .waitSeconds(0.5)
                .addTemporalMarker(() -> Servos.Wrist.goTop())
                .addTemporalMarker(() -> Servos.Slider.moveInside())
                .addTemporalMarker(() -> lift.extendTo(0, 1))
                .waitSeconds(1)
                .addTemporalMarker(() -> Servos.Wrist.goInit())
                .build();


        /**
         * Sequence for going and parking at parking zone 1
         */
        TrajectorySequence goToP1 = drive.trajectorySequenceBuilder((dropCone1.end()))
                .addTemporalMarker(() -> Servos.Wrist.goInit())
                .addTemporalMarker(() -> Servos.Gripper.closeGripper())
                .addTemporalMarker(() -> turret.setDegree(0))
                .lineToLinearHeading(new Pose2d(PARKING1.getX(), PARKING1.getY(), Math.toRadians(90)))
//                .turn(Math.toRadians(-90))

                .addTemporalMarker(() -> Servos.Slider.moveInside())
                .addTemporalMarker(() -> lift.extendTo(0, 0.5))
//                .lineToLinearHeading(new Pose2d(PARKING1.getX(), PARKING1.getY()-24, Math.toRadians(90)))
                .build();

        /**
         * Sequence for going and parking at parking zone 2
         */
        TrajectorySequence goToP2 = drive.trajectorySequenceBuilder((dropCone1.end()))
                .addTemporalMarker(() -> turret.setDegree(0))
                .addTemporalMarker(() -> Servos.Wrist.goInit())
                .addTemporalMarker(() -> Servos.Gripper.closeGripper())
                .lineToLinearHeading(new Pose2d(PARKING2.getX(), PARKING2.getY(), Math.toRadians(90)))
                .addTemporalMarker(() -> turret.setDegree(0))
                .addTemporalMarker(() -> lift.extendTo(0, 0.5))
//                .turn(Math.toRadians(-90))
//                .lineToLinearHeading(new Pose2d(PARKING2.getX(), PARKING2.getY()-24, Math.toRadians(90)))
                .addTemporalMarker(() -> Servos.Slider.moveInside())
                .build();

        /**
         * Sequence for going and parking at parking zone 3
         */
        TrajectorySequence goToP3 = drive.trajectorySequenceBuilder((dropCone1.end()))
                .addTemporalMarker(() -> turret.setDegree(0))
                .addTemporalMarker(() -> Servos.Wrist.goInit())
                .addTemporalMarker(() -> Servos.Gripper.closeGripper())
                .lineToLinearHeading(new Pose2d(PARKING3.getX(), PARKING3.getY(), Math.toRadians(90)))
                .addTemporalMarker(() -> turret.setDegree(0))
                .addTemporalMarker(() -> lift.extendTo(0, 0.5))
//                .turn(Math.toRadians(-90))
//                .lineToLinearHeading(new Pose2d(PARKING3.getX(), PARKING3.getY()-24, Math.toRadians(90)))
                .addTemporalMarker(() -> Servos.Slider.moveInside())
                .build();


//        waitForStart();
        while (!isStarted() && !isStopRequested()) {
            if(allHubs.get(0).getInputVoltage(VoltageUnit.VOLTS) < 12.2 || allHubs.get(1).getInputVoltage(VoltageUnit.VOLTS) < 12.2){
                telemetry.addLine("******************WARNING: RELIABLITY ISSUES MAY BE EXPERIENCED IN AUTO DUE TO BATTERY******************\n\n\n");
            }
            ArrayList<AprilTagDetection> currentDetections = aprilTagDetectionPipeline.getLatestDetections();
            if (currentDetections.size() != 0) {
                boolean tagFound = false;
                for (AprilTagDetection tag : currentDetections) {
                    if (tag.id == MATRIX_IDS[PARKING_ZONE1] || tag.id == MATRIX_IDS[PARKING_ZONE2] || tag.id == MATRIX_IDS[PARKING_ZONE3]) {
                        tagOfInterest = tag;
                        tagFound = true;
                        break;
                    }
                }
                if (tagFound) {
                    telemetry.addLine("Tag of interest is in sight!\n\nLocation data:");
                    tagToTelemetry(tagOfInterest);
                } else {
                    telemetry.addLine("Don't see tag of interest :(");
                    if (tagOfInterest == null) {
                        telemetry.addLine("(The tag has never been seen)");
                    } else {
                        telemetry.addLine("\nBut we HAVE seen the tag before; last seen at:");
                        tagToTelemetry(tagOfInterest);
                    }
                }
            } else {
                telemetry.addLine("Don't see tag of interest :(");
                if (tagOfInterest == null) {
                    telemetry.addLine("(The tag has never been seen)");
                } else {
                    telemetry.addLine("\nBut we HAVE seen the tag before; last seen at:");
                    tagToTelemetry(tagOfInterest);
                }
            }
            telemetry.update();
            sleep(20);
        }

        Servos.AlignBar.inside();

        AutoTime.reset();                       //Start Button has been pressed, reset the timer to keep track of the time passed in Autonomous in code
        lift.reset();                           //Reset the lift to 0, using the current position as the home position   (Usually at the hard stop)
        turret.reset();                         //Reset the turret to 0, using the current position as the home position
        turret.setDegree(0);                    //Hold the turret at 0


        //-------------------------------------- ACTUAL ROBOT TRAJ FOLLOWING TAKES PLACE HERE -----------------------------
        followTrajectory(startToCenter, drive);
        followTrajectory(pick1, drive);
        followTrajectory(dropCone1, drive);
        followTrajectory(pick2, drive);
        followTrajectory(dropCone1, drive);
        followTrajectory(pick3, drive);
        followTrajectory(dropCone1, drive);
        followTrajectory(pick4, drive);
        followTrajectory(dropCone1, drive);
//        followTrajectory(pick5, drive);
//        followTrajectory(dropCone1, drive);

//        drive.followTrajectorySequence(startToCenter);
//        drive.followTrajectorySequence(pick1);
//        drive.followTrajectorySequence(dropCone1);
//        drive.followTrajectorySequence(pick2);
//        drive.followTrajectorySequence(dropCone1);
//        drive.followTrajectorySequence(pick3);
//        drive.followTrajectorySequence(dropCone1);
//        drive.followTrajectorySequence(pick4);
//        drive.followTrajectorySequence(dropCone1);

        String ParkingZone = "3";                       //Defaults to Parking Zone 3

        if (tagOfInterest.id == MATRIX_IDS[PARKING_ZONE1]) {
            ParkingZone = "1";
            drive.followTrajectorySequence(goToP1);

        } else if (tagOfInterest.id == MATRIX_IDS[PARKING_ZONE2]) {
            ParkingZone = "2";
            drive.followTrajectorySequence(goToP2);
        } else if (tagOfInterest.id == MATRIX_IDS[PARKING_ZONE3]) {
            ParkingZone = "3";
            drive.followTrajectorySequence(goToP3);
        } else {
            drive.followTrajectorySequence(goToP3);
        }

        while (opModeIsActive()) {
            Pose2d robotPose = drive.getPoseEstimate();
            poseStorage = robotPose;
            turretAngle = turret.getDegree();
            telemetry.update();
        }
    }


    private void setInitialPositions() {
        lift.extendTo(0, 1);
        Servos.Gripper.closeGripper();
        sleep(30);
        Servos.Wrist.goInit();
        turret.setDegree(0);
    }


    /**
     * Check whether the sequence can be executed safely within time
     * @param sequence The sequence to be checked
     * @return True if the sequence is safe to be executed
     */
    private boolean checkForTime(TrajectorySequence sequence){
        double sequenceDuration = sequence.duration();
        if(sequenceDuration > 30){
            //TODO: Remove after confirming units of time
            sequenceDuration/=1000;
        }
        timeLeft = totalTime - AutoTime.seconds();
        return !(sequenceDuration > timeLeft);
    }

    /**
     * Follow a sequence, using {@link #checkForTime(TrajectorySequence) CheckforTime} to get the legality of the sequence
     * @param sequence The sequence to follow
     * @param localdrive A reference of the drive class being used for trajectory following
     */

    private void followTrajectory(TrajectorySequence sequence, SampleMecanumDrive localdrive){
        if(checkForTime(sequence) && !EmergencyParkFlag)
            localdrive.followTrajectorySequence(sequence);
        else
            EmergencyParkFlag = true;
    }

    /**
     * Get the information about the tag being detected and display it
     * @param detection The tag detection currently active
     */
    void tagToTelemetry(AprilTagDetection detection) {
        telemetry.addLine(String.format("\nDetected tag ID=%d", detection.id));
        telemetry.addLine(String.format("Translation X: %.2f feet", detection.pose.x * FEET_PER_METER));
        telemetry.addLine(String.format("Translation Y: %.2f feet", detection.pose.y * FEET_PER_METER));
        telemetry.addLine(String.format("Translation Z: %.2f feet", detection.pose.z * FEET_PER_METER));
        telemetry.addLine(String.format("Rotation Yaw: %.2f degrees", Math.toDegrees(detection.pose.yaw)));
        telemetry.addLine(String.format("Rotation Pitch: %.2f degrees", Math.toDegrees(detection.pose.pitch)));
        telemetry.addLine(String.format("Rotation Roll: %.2f degrees", Math.toDegrees(detection.pose.roll)));
    }
}
