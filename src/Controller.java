import java.io.*;

import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.Charset;

import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.LinkedList;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

public class Controller
{
  private static final String CHARSET = "US-ASCII";
  private static final int MAX_PACKET_SIZE = 1024;
  private static final int BAUD_RATE = 9600;

  private static final int HEART_BEAT_TIMEOUT = 200; //ms
  private static long last_recv_cmd_t = 0;

  //private static final String PORT_NAME = "/dev/eboard";
  private static final String PORT_NAME = "/dev/ttyUSB6"; //write udev rule to make this always /dev/eboard

  private static Queue<String> messageQueue = new LinkedList<String>();

  SerialPort serialPort;
  Thread inThread;

  private boolean connected = false;
  private OutputStream out;
  public Controller()  {


  }

  public void shutdown()
  {
  }

  protected void searchDevices()
  {
  }

  public boolean connect()
  {
    CommPortIdentifier portIdentifier = null;
    try {
      portIdentifier = CommPortIdentifier.getPortIdentifier(PORT_NAME);
    }
    catch (Exception e){
      System.err.println("Port not found");
      System.err.println(e);
      connected = false;
      return false;
    }

    if(portIdentifier.isCurrentlyOwned()){
      System.out.println("Error: Port is currently in use");
      connected = false;
      return false;
    }
    else
    {
      System.out.println("connecting");
      connected = true;
      int timeout = 2000;


        try {
          CommPort commPort = portIdentifier.open(this.getClass().getName(), timeout );
          if (commPort instanceof SerialPort) {
            serialPort = (SerialPort) commPort;
            serialPort.setSerialPortParams(BAUD_RATE,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);
          }
          else
          {
            System.out.println("Error: Not a serial port");
            return false;
          }
        }
        catch(Exception UnsupportedCommOperationException)
        {
          System.out.println("Unnsuport Comm Operation Exception");
        }
        try {
          InputStream in = serialPort.getInputStream();
          out = serialPort.getOutputStream();
          inThread = new Thread(new SerialReader(in));
          inThread.start();
        }
        catch(Exception IOException)
        {
          System.out.println("IOException");
        }


    }

    last_recv_cmd_t = System.currentTimeMillis();
    return true;
  }

  protected void disconnect()
  {
    connected = false;
    serialPort.close();
    inThread.interrupt(); //does this close?
  }

  public boolean isConnected()
  {
    File eboard_file = new File("dev/eboard");
    return eboard_file.exists();
  }

  public void send(JSONObject obj) throws IOException, ControllerException
  {
    if (!isConnected() && !connect()) {
        throw new ControllerException("Error", "Cannot send, no device found");
      }
    byte[] message = (obj + "\r\n").getBytes(CHARSET);
    out.write(message);
  }

  //this method is for testing
  public void send(String string) throws IOException
  {
    System.out.println(string);
    out.write(string.getBytes(Charset.forName(CHARSET)));
  }

  public JSONObject receive() throws IOException, ControllerException, NoSuchElementException
  {
    if (messageQueue.size() == 0)
    {
      throw new NoSuchElementException();
    }
    String line = messageQueue.remove();
    try {
      JSONObject response = new JSONObject(line);
      if (response.has("error")) {
        throw new ControllerException(response.getString("error"),
                response.optString("args"));
      }
      return response;
    } catch (JSONException e) {
      throw new IOException("Failed to parse response '" + line + "'.", e);
    }
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
  public class SerialReader implements Runnable
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
          messageQueue.add(new String(buffer,0,len));
          System.out.print(messageQueue.peek());
          if (last_recv_cmd_t - System.currentTimeMillis() > HEART_BEAT_TIMEOUT)
          {
            System.out.println("Didnt get heartbeat");
            disconnect();
          }
          else
          {
            last_recv_cmd_t = System.currentTimeMillis();
          }

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
