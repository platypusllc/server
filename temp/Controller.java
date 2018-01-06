import java.io.*;

import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.Charset;

import java.util.Enumeration;
import java.util.stream.Collectors;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import sun.misc.IOUtils;

public class Controller
{
  private static final String CHARSET = "US-ASCII";
  private static final int MAX_PACKET_SIZE = 1024;
  private static final int BAUD_RATE = 9600;

  //private static final String PORT_NAME = "/dev/eboard";
  private static final String PORT_NAME = "/dev/ttyUSB6"; //write udev rule to make this always /dev/eboard

  SerialPort serialPort;
  Thread inThread;

  private boolean isConnected = false;
  private OutputStream out;
  public Controller()  {


  }

  public void shutdown()
  {
  }

  protected void searchDevices()
  {
  }

  public boolean connect() throws Exception
  {
    CommPortIdentifier portIdentifier = null;
    try {
      portIdentifier = CommPortIdentifier.getPortIdentifier(PORT_NAME);
    }
    catch (Exception e){
      System.err.println("Port not found");
      System.err.println(e);
      isConnected = false;
      return false;
    }

    if(portIdentifier.isCurrentlyOwned()){
      System.out.println("Error: Port is currently in use");
      isConnected = false;
      return false;
    }
    else
    {
      System.out.println("connecting");
      isConnected = true;
      int timeout = 2000;
      CommPort commPort = portIdentifier.open(this.getClass().getName(), timeout );

      if(commPort instanceof SerialPort)
      {
        serialPort = (SerialPort)commPort;
        serialPort.setSerialPortParams(BAUD_RATE,
                SerialPort.DATABITS_8,
                SerialPort.STOPBITS_1,
                SerialPort.PARITY_NONE);

        InputStream in = serialPort.getInputStream();
        out = serialPort.getOutputStream();
        inThread = new Thread(new SerialReader(in));
        inThread.start();

      }
      else
      {
        System.out.println("Error: Not a serial port");
        return false;
      }
    }
    return true;
  }

  protected void disconnect()
  {
    serialPort.close();
    inThread.interrupt(); //does this close?
  }

  public boolean isConnected()
  {
    return isConnected;
  }

  public void send(JSONObject obj) throws IOException
  {
  }

  //this method is for testing
  public void send(String string) throws IOException
  {
    System.out.println(string);
    out.write(string.getBytes(Charset.forName(CHARSET)));
  }

  public JSONObject receive() throws IOException, ControllerException
  {
    return null;
  }

  public class ControllerException extends Exception { 
    public final String mArgs;

    ControllerException(String message, String args) {
      super(args.isEmpty() ? message : message + ": " + args);
      mArgs = args;
    }
  }

  /**
   * Exception caused by an action that requires hardware being called when disconnected.
   */
  public class ConnectionException extends IOException {
    ConnectionException(String message) {
      super(message);
    }
  }
  public static class SerialReader implements Runnable
  {
    InputStream in;

    public SerialReader ( InputStream in )
    {
      this.in = in;
    }
    //make this read into a buffer
    //print the buffer once null terminated, newline or carriage return
    public void run ()
    {
      byte[] buffer = new byte[1024];
      int len = -1;
      try
      {
        while ((len = this.in.read(buffer)) > -1)
        {
          System.out.print(new String(buffer,0,len));
        }
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
    }
  }

  public static void main ( String[] args ) throws Exception
  {
    try
      {
        listPorts();
        Controller mcontrol = new Controller();
        mcontrol.connect();
      }
    catch ( Exception e )
      {
        e.printStackTrace();
      }
  }
  static void listPorts()
  {
    java.util.Enumeration<CommPortIdentifier> portEnum = CommPortIdentifier.getPortIdentifiers();
    while ( portEnum.hasMoreElements() )
    {
      CommPortIdentifier portIdentifier = portEnum.nextElement();
      System.out.println(portIdentifier.getName());
    }
  }

}
