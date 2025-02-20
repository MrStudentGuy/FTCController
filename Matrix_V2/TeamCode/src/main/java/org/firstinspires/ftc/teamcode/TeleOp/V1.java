package org.firstinspires.ftc.teamcode.TeleOp;

import static org.firstinspires.ftc.teamcode.TransferClass.offsetpose;
import static org.firstinspires.ftc.teamcode.TransferClass.poseStorage;
import static org.firstinspires.ftc.teamcode.TransferClass.turretAngle;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.dashboard.telemetry.MultipleTelemetry;
import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.arcrobotics.ftclib.controller.PIDController;
import com.outoftheboxrobotics.photoncore.PhotonCore;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.Subsystems.Lift;
import org.firstinspires.ftc.teamcode.Subsystems.Sensors;
import org.firstinspires.ftc.teamcode.Subsystems.Servos;
import org.firstinspires.ftc.teamcode.Subsystems.Turret;
import org.firstinspires.ftc.teamcode.TransferClass;
import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;

import java.util.Objects;

@TeleOp(name = "-->TELEOP \uD83D\uDC4C\uD83D\uDC4C\uD83D\uDE0D\uD83C\uDFB6\uD83C\uDFB6\uD83D\uDE0E\uD83D\uDE1C\uD83D\uDE2D\uD83E\uDD70\uD83D\uDE08\uD83D\uDC7A\uD83D\uDC7A\uD83E\uDD23\uD83E\uDD23\uD83D\uDE15\uD83D\uDE1C\uD83D\uDE2D\uD83E\uDD70\uD83E\uDD70\uD83D\uDE18")
@Config
public class V1 extends LinearOpMode {
    ElapsedTime teleOpTime = new ElapsedTime();
    ElapsedTime safetyTimer = new ElapsedTime();
    public static double targetDegree = 0;
    private final double GEAR_RATIO = 10.5 * 122.0 / 18.0;
    private final double CPR = 28;             //counts
    private final double ticks_in_degree = CPR * GEAR_RATIO / 360.0;

    private boolean wristInFlag = false;

    private PIDController controller; //meant for the turret
    public static double Kp = 0.07;
    public static double Ki = 0;
    public static double Kd = 0.001;
    public static double Kf = 0; //feedforward, turret no gravity so 0


    boolean aFlag = false;
    boolean bFlag = false;
    boolean RBFlag = false;
    boolean LBFlag = false;
    boolean AutoCycleFlag = false;
    boolean AutoCycleProceed = false;


    boolean goSafeAfterReleaseFlag = false;


    ElapsedTime AutoCycleTimer = new ElapsedTime();

    double pos = 0;

    Lift lift = null;
    Servos servos = null;
    Turret turret = null;
    Sensors sensors = null;

    enum AutoCycleCenterStates {
        IDLE,
        GOGRIPPING,
        RETRACT,
        DROPANTICLOCKWISE,
        DROPCLOCKWISE,
        RETURN
    }

    AutoCycleCenterStates autoCycleCenterStates = AutoCycleCenterStates.IDLE;
    private int liftPos = 0;

    @Override
    public void runOpMode() throws InterruptedException {
        ElapsedTime teleOpTime = new ElapsedTime();
        PhotonCore.enable();
        lift = new Lift(hardwareMap, telemetry);
        servos = new Servos(hardwareMap, telemetry);
        turret = new Turret(hardwareMap, "turret", telemetry);
        sensors = new Sensors(hardwareMap, telemetry);

        controller = new PIDController(Kp, Ki, Kd);
//        telemetry = new MultipleTelemetry(telemetry, FtcDashboard.getInstance().getTelemetry());

        SampleMecanumDrive drive = new SampleMecanumDrive(hardwareMap, telemetry);

        Pose2d startPose = new Pose2d(0, 0, Math.toRadians(180));
        drive.setPoseEstimate(startPose);
//        drive.setPoseEstimate(new Pose2d(poseStorage.getX(), poseStorage.getY(), poseStorage.getHeading()+offsetpose));
        drive.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);


        while (opModeInInit()) {
            telemetry.addData("Position: ", lift.getPosition()[0] + "," + lift.getPosition()[1]);
            telemetry.update();
            if (gamepad1.dpad_up) {
                liftPos += 2;
            } else if (gamepad1.dpad_down) {
                liftPos -= 2; ///point of fakiure
            }
            lift.extendTo(liftPos, 0.5);
        }
        targetDegree = 0;

        liftPos = 0;

//        lift.reset();
//        turret.reset();

//        calibrateTurret();

        setInitialPositions();
        teleOpTime.reset();
        teleOpTime.reset();
        Servos.AlignBar.inside();


        while (opModeIsActive()) {
            controller.setPID(Kp, Ki, Kd); //turret cant use default values, too big, not accurate, hence custom values


            double currentTurretValue = turret.getDegree();

            double pid = controller.calculate(currentTurretValue, targetDegree); //output power for motor

            double ff = Math.cos(Math.toRadians(targetDegree / ticks_in_degree)) * Kf; //feedforward calculation

            double power = pid + ff; //in future in case

            turret.set(power);


            double drivePowerThrottle, drivePowerStrafe, drivePowerHeading;

            if (gamepad1.right_trigger > 0.3 || gamepad1.left_stick_button || gamepad1.right_stick_button) {       //Turn on slow mode
                drivePowerThrottle = 0.4;
                drivePowerStrafe = 0.4;
                drivePowerHeading = 0.4; //slow down - delicate situation
            } else {
                drivePowerThrottle = 1;
                drivePowerStrafe = 1;
                drivePowerHeading = 0.7; //(turning)
            }


            //Field Oriented Drive
            Pose2d poseEstimate = drive.getPoseEstimate();
            Vector2d input = new Vector2d(-gamepad1.left_stick_y * drivePowerThrottle, -gamepad1.left_stick_x * drivePowerStrafe).rotated(-poseEstimate.getHeading());

            drive.setWeightedDrivePower(new Pose2d(input.getX(), input.getY(), -gamepad1.right_stick_x * drivePowerHeading));
            drive.update();//drive is sample mecanum drive


            //UNCOMMENT FOLLOWING FOR ROBOT ORIENTED DRIVE
//            drive.setWeightedDrivePower(
//                    new Pose2d(
//                            -gamepad1.left_stick_y * 0.4,
//                            -gamepad1.left_stick_x * 0.4,
//                            -gamepad1.right_stick_x * 0.6
//                    )
//            );
//
//            drive.update();
//
//            Pose2d poseEstimate = drive.getPoseEstimate();


            boolean A = gamepad1.a;                  //x
            boolean B = gamepad1.b;                  //o
            boolean UP = gamepad1.dpad_up;
            boolean RIGHT = gamepad1.dpad_right;
            boolean DOWN = gamepad1.dpad_down;
            boolean LEFT = gamepad1.dpad_left;
            boolean RB = gamepad1.right_bumper;
            boolean LB = gamepad1.left_bumper;
            boolean R3 = gamepad1.right_stick_button;
            boolean L3 = gamepad1.left_stick_button;


//            if(gamepad1.right_stick_button && lift.getPosition()[0] >= lift.POSITIONS[lift.LOW_POLE]){
//                Servos.Slider.moveOutside();
//                sleep(1000);
//                Servos.AlignBar.outside();
//                sleep(1000);
//            }

            boolean UP2 = gamepad2.dpad_up;
            boolean DOWN2 = gamepad2.dpad_down;
            boolean LEFT2 = gamepad2.dpad_left;
            boolean RIGHT2 = gamepad2.dpad_right;

            boolean A2 = gamepad2.a;
            boolean B2 = gamepad2.b;
            boolean X2 = gamepad2.x;
            boolean Y2 = gamepad2.y;


            if (gamepad1.a || wristInFlag) {
                if (lift.getPosition()[0] >= 59) {
                    wristInFlag = false;
                    Servos.Wrist.goInit();
                    Servos.Gripper.closeGripper();
                } else {
                    wristInFlag = true;
                    lift.extendTo(60, 1);
                }
            }


            if (A2) { //incase finished all 18, auto not used, then use auto stack
                Servos.Wrist.goGripping();
                lift.extendTo(lift.AUTO_POSITION[4], 0.8);
            } else if (B2 || gamepad1.triangle) {
                Servos.Wrist.goGripping();
                lift.extendTo(lift.AUTO_POSITION[3], 0.8);
            } else if (Y2) {
                Servos.Wrist.goGripping();
                lift.extendTo(lift.AUTO_POSITION[2], 0.8);
            } else if (X2) {
                Servos.Wrist.goGripping();
                lift.extendTo(lift.AUTO_POSITION[1], 0.8);
            }


            if (gamepad2.right_bumper && !AutoCycleFlag) {
                AutoCycleFlag = true;
                AutoCycleTimer.reset();
                autoCycleCenterStates = AutoCycleCenterStates.GOGRIPPING;
            }
            if (gamepad2.start) {
                autoCycleCenterStates = AutoCycleCenterStates.IDLE;
            }
            if (AutoCycleFlag) {
                switch (autoCycleCenterStates) {
                    case IDLE:
                        AutoCycleFlag = false;
                        AutoCycleProceed = false;
                        turret.setDegree(0);
                        lift.extendToLowPole();
                        Servos.Slider.moveInside();
                        Servos.Gripper.openGripper();
                        break;

                    case GOGRIPPING:
                        AutoCycleFlag = true;
                        Servos.Slider.moveOutside();
                        Servos.Gripper.openGripper();
                        lift.extendToGrippingPosition();
                        turret.setDegree(0);
                        Servos.Slider.moveOutside();
                        if (AutoCycleTimer.milliseconds() > 1400) {
                            Servos.Gripper.closeGripper();
                            Servos.Slider.moveHalfway();
                            autoCycleCenterStates = AutoCycleCenterStates.RETRACT;
                            AutoCycleTimer.reset();
                        }
                        break;

                    case RETRACT:
                        if (gamepad2.right_bumper) {
                            lift.extendToHighPole();
                            sleep(500);
                            turret.setDegreeHighPower(150);
                            sleep(800);
                            turret.setDegree(170);
                            sleep(400);
                            Servos.Slider.moveSlider(0.65);
                            sleep(800);
                            Servos.Wrist.goAutoTop();
                            sleep(200);
                            Servos.Wrist.goGripping();
                            sleep(500);
                            Servos.Gripper.setPosition(1);
//                Servos.AlignBar.inside();
                            sleep(50);
                            Servos.Slider.moveHalfway();
                            sleep(50);
                            autoCycleCenterStates = AutoCycleCenterStates.IDLE;
//                        sleep(500);

                            AutoCycleTimer.reset();
                        } else if (gamepad2.left_bumper) {
                            autoCycleCenterStates = AutoCycleCenterStates.GOGRIPPING;
                            AutoCycleTimer.reset();
                        }
                        break;

                }

            } else {
                Servos.Slider.moveSlider(Math.abs(1 - gamepad1.left_trigger));
            }

            if (UP) {
                Servos.Wrist.goAutoTop();
                lift.extendToHighPole();
                Servos.AlignBar.teleopOut();
            } else if ((RIGHT || gamepad2.dpad_down)) {
                Servos.Wrist.goGripping();
                lift.extendToGrippingPosition();
                Servos.AlignBar.inside();
            } else if (DOWN) {
                Servos.Wrist.goAutoTop();
                lift.extendToLowPole();
                Servos.AlignBar.interMediate();
            } else if (LEFT) {
                Servos.Wrist.goAutoTop();
                lift.extendToMidPole();
                Servos.AlignBar.teleopOut();
            }


            if (gamepad2.start || gamepad1.start) {
                Servos.Gripper.gripperState = "BEACON";
            }


            if (RB && !RBFlag) {
                RBFlag = true; //only once
                if (Objects.equals(Servos.Gripper.gripperState, "OPEN")) {
                    Servos.Wrist.goGripping();
                    Servos.Gripper.closeGripper();
                } else if (Objects.equals(Servos.Gripper.gripperState, "CLOSED")) {


                    if (lift.getPosition()[0] > lift.POSITIONS[lift.LOW_POLE] - 30) {
//                        Servos.Slider.moveSlider(0.5);
//                        sleep(300);
                        Servos.Wrist.goGripping();
                        if(lift.getPosition()[0] > lift.POSITIONS[lift.LOW_POLE]+30) {
                            Servos.AlignBar.moveTo(0.3);
                        }

                        goSafeAfterReleaseFlag = true;
                        safetyTimer.reset();
                    } else {
                        Servos.Gripper.openGripper();
                    }

//                    if(lift.getPosition()[0] > lift.POSITIONS[lift.LOW_POLE]) {
//                        Servos.AlignBar.moveTo(0.7);
//                        if (Servos.AlignBar.getPosition() > 0.4) {
//                            Servos.Slider.moveteleopOut;
//                            sleep(1000);
//                            Servos.AlignBar.inside();
//                            sleep(500);
//                            Servos.Slider.moveInside();
//                            sleep(300);
//                        }
//                    }
                } else if (Objects.equals(Servos.Gripper.gripperState, "BEACON")) {
                    Servos.Gripper.gripBeacon();
                }
            }

            if (LB && !LBFlag) {
                LBFlag = true;
                if (Objects.equals(Servos.Wrist.wristState, "GRIPPING")) {
                    Servos.Wrist.goAutoTop();
                    Servos.AlignBar.interMediate();
                    if (lift.getPosition()[0] < lift.POSITIONS[lift.MID_POLE] - 30) {
                        Servos.AlignBar.interMediate();
                    }
                    else{
                        Servos.AlignBar.teleopOut();
                    }
                } else if (Objects.equals(Servos.Wrist.wristState, "TOP")) {
                    Servos.AlignBar.inside();
                    Servos.Wrist.goGripping();
                } else {
                    Servos.Wrist.goGripping();
                }
            }
            telemetry.addData("LbFlag", LBFlag);

            if (!RB) {
                RBFlag = false;
            }
            if (!LB) {
                LBFlag = false;
            }
            if ((LEFT2) && !aFlag) {
//            if ((gamepad1.square || LEFT2) && !aFlag) {
                aFlag = true;
                targetDegree += 90;
                setTurret();

            } else if (!LEFT2) {
//            } else if (!gamepad1.square && !LEFT2) {
                aFlag = false;
            }
            if (RIGHT2 && !bFlag) {
//            if ((B || RIGHT2) && !bFlag) {
                bFlag = true;
                targetDegree -= 90;
                setTurret();
            } else if (!RIGHT2) {
//            } else if (!B && !RIGHT2) {
                bFlag = false;
            }

            if (gamepad1.touchpad && lift.getPosition()[0] >= lift.POSITIONS[lift.MID_POLE]) {
                targetDegree = 0;
            }


//            if (Sensors.GripperSensor.getDistanceMM() < 25 && Sensors.GripperSensor.getDistanceMM() > 10 && Servos.Gripper.gripperState != "CLOSED" && Servos.Wrist.wristState == "GRIPPING") {
//                gamepad1.rumble(0.5, 0.5, 100);
//            }

            if (goSafeAfterReleaseFlag) {
                if(safetyTimer.milliseconds() >= 400 && safetyTimer.milliseconds() < 600){
                    Servos.Gripper.openGripper();
                }

                if (safetyTimer.milliseconds() >= 400 && safetyTimer.milliseconds() < 1200) {
                    if (lift.getPosition()[0] > lift.POSITIONS[lift.LOW_POLE]) {
                        targetDegree = 0;
                    }
                    if (safetyTimer.milliseconds() >= 800 && safetyTimer.milliseconds() < 1000) {
                        Servos.Gripper.closeGripper();
                        Servos.AlignBar.inside();
                    }
                    if (safetyTimer.milliseconds() >= 1000) {
                        lift.extendTo(lift.LOW_POLE, 1);
                        goSafeAfterReleaseFlag = false;
                    }
                }
            }

//            if(teleOpTime.seconds())


//            lift.extendTo((int) liftPos);


            telemetry.addData("Currents: ", lift.getCurrent()[0] + ", " + lift.getCurrent()[1]);
            telemetry.addData("Positions: ", lift.getPosition()[0] + ", " + lift.getPosition()[1]);
            telemetry.addData("x", poseEstimate.getX());
            telemetry.addData("y", poseEstimate.getY());
            telemetry.addData("heading", poseEstimate.getHeading());
            telemetry.addData("Turret Angle: ", turret.getDegree());
            telemetry.update();
        }
    }


    private void setInitialPositions() {
        lift.extendTo(0, 0.5);
        Servos.Gripper.closeGripper();
        sleep(30);
        Servos.Wrist.goInit();
        pos = 0;
        turret.setDegree(0);
    }

    private void setTurret() {
        if (lift.getPosition()[0] < lift.SAFE_POSITION) {
//do nothing
        } else {
//            turret.setDegree((int) (pos));
        }
    }

    @Deprecated
    private void calibrateTurret(){
        double currentDelta = turret.getDegree() - turretAngle;
        if(Math.abs(currentDelta)>1){                           //if the current error has an absolute value of greater than 1 degree, rotate the turret in the appropriate direction
            turret.setDegreeHighPower(currentDelta);
        }
        sleep(1000);          //give the turret time to reach it's target
        turret.reset();                 //reset so that telop has a calibrated turret
    }
}