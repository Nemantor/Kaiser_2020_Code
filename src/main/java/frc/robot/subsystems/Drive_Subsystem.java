package frc.robot.subsystems;

import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;
import com.ctre.phoenix.motorcontrol.can.TalonSRXConfiguration;


import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.DemandType;
import edu.wpi.first.wpilibj.SPI;
import edu.wpi.first.wpilibj.drive.DifferentialDrive;
import edu.wpi.first.wpilibj.geometry.Pose2d;
import edu.wpi.first.wpilibj.geometry.Rotation2d;
import edu.wpi.first.wpilibj.kinematics.DifferentialDriveOdometry;
import edu.wpi.first.wpilibj.kinematics.DifferentialDriveWheelSpeeds;
import edu.wpi.first.wpilibj.trajectory.Trajectory;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.RamseteCommand;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.DriveConstants;
import com.kauailabs.navx.frc.AHRS;
import edu.wpi.first.wpilibj.controller.RamseteController;




public class Drive_Subsystem extends SubsystemBase {
  /**
   * Creates a new DriveSubsystem.
   */

  private final WPI_TalonSRX left_Master_Motor = new WPI_TalonSRX(DriveConstants.kLeft_Master_Port);
  private final WPI_TalonSRX left_Slave_Motor = new WPI_TalonSRX(DriveConstants.kLeft_Slave_Port);

  private final WPI_TalonSRX right_Master_Motor = new WPI_TalonSRX(DriveConstants.kRight_Master_Port);
  private final WPI_TalonSRX right_Slave_Motor = new WPI_TalonSRX(DriveConstants.kRight_Slave_Port);

  private DifferentialDrive m_drive = new DifferentialDrive(left_Master_Motor, right_Master_Motor);
  public DifferentialDriveOdometry m_odometry;
  public final AHRS m_gyro = new AHRS(SPI.Port.kMXP);

  private double target;
  private boolean isSlowMode = false;

  
  public Drive_Subsystem() {

    resetEncoders();
    m_odometry = new DifferentialDriveOdometry(Rotation2d.fromDegrees(getHeading()));

    left_Slave_Motor.follow(left_Master_Motor);
    right_Slave_Motor.follow(right_Master_Motor);

    TalonSRXConfiguration talonConfig = new TalonSRXConfiguration();
    talonConfig.primaryPID.selectedFeedbackSensor = FeedbackDevice.QuadEncoder;
    talonConfig.slot0.kP = DriveConstants.kP;
    talonConfig.slot0.kI = DriveConstants.kI;
    talonConfig.slot0.kD = DriveConstants.kD;
    talonConfig.slot0.integralZone = 400;
    talonConfig.slot0.closedLoopPeakOutput = 1.0;
    talonConfig.openloopRamp = DriveConstants.kOpen_Loop_Ramp;

    left_Master_Motor.configAllSettings(talonConfig);
    right_Master_Motor.configAllSettings(talonConfig);

    resetEncoders();

    right_Master_Motor.setInverted(false);
    left_Master_Motor.setInverted(false);

    left_Slave_Motor.setInverted(false);
    right_Slave_Motor.setInverted(true);

    right_Master_Motor.setSensorPhase(true);
    left_Master_Motor.setSensorPhase(true);

    right_Master_Motor.setSafetyEnabled(false);
    right_Slave_Motor.setSafetyEnabled(false);
    left_Master_Motor.setSafetyEnabled(false);
    left_Slave_Motor.setSafetyEnabled(false);
    m_drive.setSafetyEnabled(false);

    left_Master_Motor.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Relative, 0, 10);
    right_Master_Motor.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Relative, 0, 10);

    left_Master_Motor.setSensorPhase(true);
    right_Master_Motor.setSensorPhase(true);
    
    right_Master_Motor.setInverted(true);


  }


  @Override
  public void periodic() {
    m_odometry.update(Rotation2d.fromDegrees(getHeading()), 
    getLeftEncoderDistance(), 
    getRightEncoderDistance());
  }

  public Pose2d getPose() {
    return m_odometry.getPoseMeters();
  }


  public DifferentialDriveWheelSpeeds getWheelSpeeds() {
    return new DifferentialDriveWheelSpeeds(
        10.0 * left_Master_Motor.getSelectedSensorVelocity() * DriveConstants.kWheel_Circumference_Meters/ DriveConstants.kEncoder_CPR,
        10.0 * right_Master_Motor.getSelectedSensorVelocity() * DriveConstants.kWheel_Circumference_Meters/ DriveConstants.kEncoder_CPR);
  }

  public void arcadeDrive(double speed, double rotation, boolean useSquares) {

    var xSpeed = speed;
    var zRotation = rotation;

    if (xSpeed == 1) {
      zRotation *= 0.5;
    }
    else zRotation *=0.7;


    if (useSquares) {
      xSpeed *= Math.abs(xSpeed);
      zRotation *= Math.abs(zRotation);
    }

    if (isSlowMode) {
      xSpeed *= 0.3;
      zRotation *= 0.47;

    }

    xSpeed = Deadband(xSpeed);
    zRotation = Deadband(zRotation);
    
		left_Master_Motor.set(ControlMode.PercentOutput, xSpeed, DemandType.ArbitraryFeedForward, +zRotation);
	  right_Master_Motor.set(ControlMode.PercentOutput, xSpeed, DemandType.ArbitraryFeedForward, -zRotation);

  }

  public void tankDriveVelocity(double leftVelocity, double rightVelocity) {
    var leftFeedForwardVolts = DriveConstants.FEED_FORWARD.calculate(leftVelocity)/10;
   var rightFeedForwardVolts = DriveConstants.FEED_FORWARD.calculate(rightVelocity)/10;

    left_Master_Motor.set(
        ControlMode.Velocity, 
        metersPerSecToEdgesPerDecisec(leftVelocity),    
        DemandType.ArbitraryFeedForward,
        leftFeedForwardVolts);
    right_Master_Motor.set(
        ControlMode.Velocity,
        metersPerSecToEdgesPerDecisec(rightVelocity),
        DemandType.ArbitraryFeedForward,
        rightFeedForwardVolts);
  }

  public void resetOdometry() {
    resetEncoders();
    zeroHeading();
    m_odometry.resetPosition(new Pose2d(0, 0, Rotation2d.fromDegrees(0)), Rotation2d.fromDegrees(0));
    System.out.print("resseeet");

  }






  public void tankDriveVolts(double leftVolts, double rightVolts) {
    left_Master_Motor.setVoltage(leftVolts);
    right_Master_Motor.setVoltage(rightVolts);                   //todo
    m_drive.feed();
    System.out.print("Left: ");
    System.out.println(leftVolts);
    System.out.print("Right: ");
    System.out.println(rightVolts);
  
  }

  public Command createCommandForTrajectory(Trajectory trajectory) {
    return new RamseteCommand(
            trajectory,
            this::getPose,
            new RamseteController(DriveConstants.kRamsete_B, DriveConstants.kRamsete_Zeta),
            DriveConstants.kDrive_Kinematics,
            this::tankDriveVelocity,
            this);
  }


  

  public double getRightEncoderDistance() {
    return right_Master_Motor.getSelectedSensorPosition() * DriveConstants.kWheel_Circumference_Meters/ DriveConstants.kEncoder_CPR;
  }

  public double getLeftEncoderDistance() {
    return left_Master_Motor.getSelectedSensorPosition(0) * DriveConstants.kWheel_Circumference_Meters/ DriveConstants.kEncoder_CPR;
  }

  public double getAverageEncoderDistance() {
    return (getRightEncoderDistance() + getLeftEncoderDistance()) / (2.0);
  }

  public void setMaxOutput(double maxOutput) {
    m_drive.setMaxOutput(maxOutput);
  }

  public double getTarget() {
    return getHeading() + target;
  }

  public void setTarget(double val) {
    target = val;
  }

  public void arcadeDrive(double fwd, double rot)   {
    m_drive.arcadeDrive(fwd, rot, true);
  }

  public void zeroHeading() {
    m_gyro.reset();
  }

  public double getHeading() {
    return Math.IEEEremainder(m_gyro.getAngle(), 360.0d) * -1.0d;                         
  }

  public double getTurnRate() {
    return m_gyro.getRate() * (DriveConstants.kGyro_Reversed ? -1.0 : 1.0);              
  }

  public double getHeadingCW() {
    // Not negating
    return Math.IEEEremainder(m_gyro.getAngle(), 360);
  }

  public double getTurnRateCW() {
    // Not negating
    return m_gyro.getRate();
  }

  public void resetEncoders() {
    right_Master_Motor.setSelectedSensorPosition(0);
    left_Master_Motor.setSelectedSensorPosition(0);
  }

  public static double metersPerSecToEdgesPerDecisec(double metersPerSec) {
    return metersToEdges(metersPerSec) * .1d;
  }

  public static double metersToEdges(double meters) {
    return (meters / DriveConstants.kWheel_Circumference_Meters) * DriveConstants.kEncoder_CPR;
  }

  double Deadband(double value) {
		/* Upper deadband */
		if (value >= +0.09) 
			return value;
		
		/* Lower deadband */
		if (value <= -0.09)
			return value;
		
		/* Outside deadband */
		return 0;
  }
  
  public void setBrake() {
    left_Master_Motor.setNeutralMode(NeutralMode.Brake);
    right_Master_Motor.setNeutralMode(NeutralMode.Brake);

  }

  public void setCoast() {
    left_Master_Motor.setNeutralMode(NeutralMode.Coast);
    right_Master_Motor.setNeutralMode(NeutralMode.Coast);

  }

  public void stop() {
    left_Master_Motor.set(ControlMode.PercentOutput, 0);
    right_Master_Motor.set(ControlMode.PercentOutput, 0);


  }

  public void setVoltageComp () {
    left_Master_Motor.enableVoltageCompensation(true);
    left_Master_Motor.configVoltageCompSaturation(10);

    right_Master_Motor.enableVoltageCompensation(true);
    right_Master_Motor.configVoltageCompSaturation(10);

  }

  public void disableVoltageComp () {
    left_Master_Motor.enableVoltageCompensation(false);
    right_Master_Motor.enableVoltageCompensation(false);


  }


  public void changeSlowMode() {
    if(isSlowMode) isSlowMode=false;
    else isSlowMode = true;

  }

}