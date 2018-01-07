import java.io.*;

import gnu.io.*;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.Charset;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Controller
{
  private static final String CHARSET = "US-ASCII";
  private static final int MAX_PACKET_SIZE = 1024;
  private static final int BAUD_RATE = 115200;

  private static final int HEART_BEAT_TIMEOUT = 200; //ms
  private static long last_recv_cmd_t = 0;

  //private static final String PORT_NAME = "/dev/eboard";
  private static final String PORT_NAME = "/dev/ttyACM0"; //write udev rule to make this always /dev/eboard

  private static Queue<String> messageQueue = new LinkedList<String>();

  SerialPort serialPort;
  Thread inThread;

  private boolean connected = false;
  private OutputStream out;

  public static Logger logger = Logger.getLogger(Controller.class.getName());

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
      //logger.log(Level.parse("ERROR"),"Port not found.",e);
      logger.log(Level.SEVERE,"Port not found.",e);
      connected = false;
      return false;
    }

    if(portIdentifier.isCurrentlyOwned()){
      logger.log(Level.parse("ERROR"),"Port is currently in use.");
      connected = false;
      return false;
    }
    else
    {
      logger.log(Level.INFO,"Connecting to eboard.");
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
            logger.log(Level.parse("ERROR"),"Not a serial port.");
            return false;
          }
        }
        catch(UnsupportedCommOperationException e)
        {
          logger.log(Level.parse("ERROR"),"Unsupported Comm Operation.",e);
        }
        catch(PortInUseException e)
        {
          logger.log(Level.parse("ERROR"),"Port in use.",e);
        }
        try {
          InputStream in = serialPort.getInputStream();
          out = serialPort.getOutputStream();
          inThread = new Thread(new SerialReader(in));
          inThread.start();
        }
        catch(IOException e)
        {
          logger.log(Level.parse("ERROR"),"IOException with streams.",e);
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
    //System.out.println("REC: " + line);
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
  public class SerialReader implements Runnable {

    Reader reader;
    public SerialReader(InputStream in) {
      this.reader = new InputStreamReader(in, StandardCharsets.US_ASCII);
    }

    //make this read into a buffer
    //print the buffer once null terminated, newline or carriage return
    public void run() {
      try {
        int c;
        char[] buffer = new char[100];
        int index = 0;
        while ((c = reader.read()) != -1) {
          char ch = (char) c;

          if (ch != '\n' && ch != '\r') {
            buffer[index++] = ch;
          }
          else if (ch == '\n' || ch == '\r') {
            buffer[index] = '\0';
            String command = new String(buffer,0,index);
            //System.out.println(command);
            messageQueue.add(command);
            index = 0;
            buffer[0] = '\0';

          }
        }
      }
      catch(IOException e)
      {
        logger.log(Level.WARNING, "Could not open IOstream",e);
      }
    }
  }


  public static void main ( String[] args ) throws Exception
  {
    System.setProperty("gnu.io.rxtx.SerialPorts", PORT_NAME);
    try
      {
    //    listPorts();
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
  public String getPortName()
  {
    return PORT_NAME;
  }
}
