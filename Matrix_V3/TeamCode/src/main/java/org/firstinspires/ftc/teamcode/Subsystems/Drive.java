package org.firstinspires.ftc.teamcode.Subsystems;

import androidx.annotation.GuardedBy;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.arcrobotics.ftclib.command.SubsystemBase;
import com.qualcomm.hardware.bosch.BNO055IMU;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.GlobalVars;
import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;

import java.util.function.DoubleSupplier;

public class Drive extends SubsystemBase {
    SampleMecanumDrive drive;
    OpMode opMode;
    Gamepad gamepad;
    Telemetry telemetry;
    Pose2d startPose = new Pose2d(0,0,0);
    double drivePowerThrottle, drivePowerStrafe, drivePowerHeading;

    private final Object imuLock = new Object();
    @GuardedBy("imuLock")

    private BNO055IMU imu;
    double heading;
    public static double  headingVel;
    Thread imuThread;

    public DoubleSupplier RobotHeadingSupplier = new DoubleSupplier() {
        @Override
        public double getAsDouble() {
            return heading;
        }
    };

    public Drive(HardwareMap hardwareMap, Telemetry telemetry, OpMode opMode){
        this.drive = new SampleMecanumDrive(hardwareMap, telemetry, RobotHeadingSupplier);
        this.opMode = opMode;
        this.telemetry = telemetry;
        gamepad = opMode.gamepad1;
        drive.setPoseEstimate(startPose);
        imuThread = new Thread(()->{
            synchronized (imuLock){
                heading = imu.getAngularOrientation().firstAngle;
                headingVel = imu.getAngularVelocity().zRotationRate;
            }
        });
        synchronized (imuLock) {
            imu = hardwareMap.get(BNO055IMU.class, "imu");
            BNO055IMU.Parameters parameters = new BNO055IMU.Parameters();
            parameters.angleUnit = BNO055IMU.AngleUnit.RADIANS;
            imu.initialize(parameters);
        }
    }
    @Override
    public void periodic() {
        super.periodic();
        if (gamepad.right_trigger > 0.3 || gamepad.left_stick_button || gamepad.right_stick_button) {       //Turn on slow mode
            GlobalVars.slowDriveFlag = true;
        } else {
            GlobalVars.slowDriveFlag = false;
        }

        if(GlobalVars.slowDriveFlag){
            slowDownDrive();
        }
        else{
            normalSpeedDrive();
        }

        if(GlobalVars.driveTelemetry) {
            telemetry.addData("Heading: ", heading);
        }
        Vector2d input = new Vector2d(-gamepad.left_stick_y * drivePowerThrottle, -gamepad.left_stick_x * drivePowerStrafe).rotated(-heading);
        drive.setWeightedDrivePower(new Pose2d(input.getX(), input.getY(), -gamepad.right_stick_x * drivePowerHeading));
    }

    public void startIMUThread(){
        imuThread.start();
    }

    public void slowDownDrive(){
        drivePowerThrottle = 0.4;
        drivePowerStrafe = 0.4;
        drivePowerHeading = 0.4;
    }

    public void normalSpeedDrive(){
        drivePowerThrottle = 1;
        drivePowerStrafe = 1;
        drivePowerHeading = 0.7;
    }

    public void killIMUThread(){
        imuThread.interrupt();
    }

    public void resetHeading(){
        drive.setPoseEstimate(startPose);
    }
}
