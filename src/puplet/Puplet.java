/*
 * Puplet.java
 *
 * Created on 8 december 2004, 12:35
 */

package puplet;

import java.io.IOException;

import java.util.Vector;
import java.util.Hashtable;

import javax.bluetooth.*;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

/**
 * This object handles the clientside of the Bluetooth communication.
 * 
 * @author Elmar Keij, Richard Kettelerij, Nicolai Lourens, Tim Prijn, Julien
 *         Rentrop
 * 
 * @version 0.1
 */
class BluetoothClient {
	// Describes the server looking for
	private static final UUID BT_SERVER_UUID = new UUID(
			"8A0FC4E421B011D9A39735D79653C90E", false);

	// Text attribute for friendly name
	public static final int FRIENDLY_NAME_ATTRIBUTE_ID = 0x4321;

	// UUID array for retrieving service information (searchServices)
	private UUID[] uuidSet = new UUID[1];

	// Keeps the discovery agent reference.
	private DiscoveryAgent discoveryAgent;

	// Collects the found remote devices
	private Vector remoteDevices = new Vector();

	// Collects the found services
	private Vector services = new Vector();

	// Pointer to attribute ID is saved
	private int[] attrSet = new int[1];

	// class is used for discovery
	private DeviceDiscovery discover = new DeviceDiscovery();

	/**
	 * Constructs an instance of this class
	 */
	public BluetoothClient() {
		// Set uuidSet for later use in determineServicesAvailable
		uuidSet[0] = BT_SERVER_UUID;
		// Set the attrset for retrieving friendly name of server telephone
		attrSet[0] = FRIENDLY_NAME_ATTRIBUTE_ID;

		// Initialize local Bluetooth device for client processing
		try {
			LocalDevice localDevice = LocalDevice.getLocalDevice();
			// Needed for searching services and devices
			discoveryAgent = localDevice.getDiscoveryAgent();
		} catch (BluetoothStateException e) {
			System.err.println("Client could not establish connection: "
					+ e.getMessage());
		}
	}

	/**
	 * Start discovering devices and return found ones
	 */
	public Vector discoverDevices() {
		try {
			discover.searchDevices();
			discover.determineServicesAvailable();
		} catch (BluetoothStateException e) {
			System.err.println("Bluetooth is malfunctioning");
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.err
					.println("Device and/or service discovery proccess canceled");
			e.printStackTrace();
		}

		return services;
	}

	/**
	 * This method starts the thread that send message
	 * 
	 * @param connectionURL
	 *            the location to connect to
	 * @param frame
	 *            the message to send (as a array of raw bytes)
	 */
	public void sendMessage(String connectionURL, byte[] frame) {
		// Sending is processed in thread
		MessageSender msg = new MessageSender(connectionURL, frame);
		Thread t = new Thread(msg);
		t.start();
		//Aanpassing
		try{
			t.join();
		}catch(InterruptedException e){
			e.printStackTrace();
		}
	}

	/**
	 * This class provides the methods for discovering of devices and sending of
	 * a message. Thread is used to prevent deadlock and make sure every request
	 * is handled
	 * 
	 * @author Elmar Keij, Richard Kettelerij, Nicolai Lourens, Tim Prijn,
	 *         Julien Rentrop
	 * 
	 * @version 0.1
	 */
	private class DeviceDiscovery implements DiscoveryListener {
		// Keeps the device discovery return code.
		private int discType;

		// Keeps the service discovery return code.
		private int servType;

		/**
		 * This method finds all devices in range.
		 */
		private synchronized void searchDevices()
				throws BluetoothStateException, InterruptedException {
			// Clear Vector from previous search
			remoteDevices.removeAllElements();

			try {
				discoveryAgent.startInquiry(DiscoveryAgent.GIAC, this);
			} catch (BluetoothStateException e) {
				throw new BluetoothStateException("Device discovery error");
			}

			// Wait until devices are found
			try {
				wait();
			} catch (InterruptedException e) {
				throw new InterruptedException(
						"Device discovery unexpected terminated.");
			}

			// Return value of device discovery
			switch (discType) {
			case INQUIRY_ERROR:
				throw new BluetoothStateException("Device discovery error.");
			case INQUIRY_COMPLETED:
				if (remoteDevices.size() == 0) {
					throw new BluetoothStateException("No devices found.");
				}
				break;
			default:
				throw new BluetoothStateException("Bluetooth overflow.");
			}
		}

		/**
		 * Invoked by system when device discovery is done. Remember the
		 * discType and process its evaluation in another thread.
		 * 
		 * @param discType
		 *            result code of the inquiry proccess
		 */
		public void inquiryCompleted(int discType) {
			// Make sure discType is not modified in other thread
			synchronized (this) {
				this.discType = discType;
				notify();
			}
		}

		/**
		 * Invoked by system when a new remote device is found - remember the
		 * found device.
		 * 
		 * @param btDevice
		 *            an object representing the remote bluetooth device
		 * @param btClass
		 *            the type of device (i.e. Phone, PDA, etc)
		 */
		public void deviceDiscovered(RemoteDevice btDevice, DeviceClass btClass) {
			// Same device may be found several times during single search
			if (remoteDevices.indexOf(btDevice) == -1) {
				remoteDevices.addElement(btDevice);
			}
		}

		/**
		 * Determine which service are available on the remote device.
		 */
		private synchronized void determineServicesAvailable()
				throws BluetoothStateException, InterruptedException {
			services.removeAllElements();
			RemoteDevice rd;
			// For all devices find out if they provide the right service
			for (int i = 0; i < remoteDevices.size(); i++) {
				rd = (RemoteDevice) remoteDevices.elementAt(i);
				try {
					// Search for btServer UUID on rd
					discoveryAgent.searchServices(attrSet, uuidSet, rd, this);
				} catch (BluetoothStateException e) {
					System.out.println("Exception occurred for device: "
							+ rd.toString());
				}

				try {
					wait();
				} catch (InterruptedException ie) {
					throw new InterruptedException("Interrupt has occured");
				}

				// Return value of service discovery
				if (servType == SERVICE_SEARCH_ERROR) {
					System.out.println("Service Discovery failed for device: "
							+ rd.toString());
				}
			}
		}

		/**
		 * @see javax.bluetooth.DiscoveryListener#servicesDiscovered(int,
		 *      javax.bluetooth.ServiceRecord[]) pre: This method is only called
		 *      when requested service is found
		 */
		public void servicesDiscovered(int transID, ServiceRecord[] servRec) {
			// Localdevice can also populate the client device in
			// some implementations
			if (servRec[0].getHostDevice() != null) {
				services.addElement(servRec[0]);
			}
		}

		/**
		 * result of search is saved for use in determineServicesAvailable()
		 * 
		 * @see javax.bluetooth.DiscoveryListener#serviceSearchCompleted(int,
		 *      int)
		 */
		public void serviceSearchCompleted(int n, int servType) {
			// Make sure servType is not modified in other thread
			synchronized (this) {
				this.servType = servType;
				notify();
			}
		}
	}

	/**
	 * This class starts itself in a thread and sends the message
	 * 
	 * @author Elmar Keij, Richard Kettelerij, Nicolai Lourens, Tim Prijn,
	 *         Julien Rentrop
	 * 
	 * @version 0.1
	 */
	private class MessageSender implements Runnable {
		private byte[] frame;
		private String connectionURL;

		/**
		 * Constructs an instance of this class
		 * 
		 * @param connectionURL
		 *            the location to connect to
		 * @param frame
		 *            the message to send (as a array of raw bytes)
		 */
		public MessageSender(String connectionURL, byte[] frame) {
			this.frame = frame;
			this.connectionURL = connectionURL;
		}

		/**
		 * Run the thread code
		 */
		public void run() {
			L2CAPConnection connToServ = null;

			try {
				// Get connection to server
				connToServ = (L2CAPConnection) Connector.open(connectionURL);

				// Send byte[] frame
				connToServ.send(frame);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				try {
					connToServ.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}
	}

}

/**
 * This object handles the serverside of the Bluetooth communication.
 */
class BluetoothServer implements Runnable {
	// Pointer to parent necessary for updating GUI
	private Puplet parent;

	// Describes this server
	private static final UUID SERVER_UUID = new UUID(
			"8A0FC4E421B011D9A39735D79653C90E", false);

	// Text attribute for friendly name
	private static final int FRIENDLY_NAME_ATTRIBUTE_ID = 0x4321;

	// Keeps friendly name in service record
	private static DataElement friendlyName;

	// Accepts new connections.
	private L2CAPConnectionNotifier connectionNotifier;

	// Keeps the information about this server.
	private ServiceRecord record;

	// Becomes 'true' when this component is finalized.
	private boolean isClosed;

	// Creates notifier and accepts clients to be processed.
	private Thread accepterThread;

	// Process the particular client from queue.
	private ClientProcessor clientProcessor;

	/**
	 * Constructs an instance of this class
	 * 
	 * @param parent
	 *            instance of the PhoneCommunicator, necessary for updating GUI
	 * @param friendlyNameStr
	 *            the name of the localdevice defined with setName()
	 */
	public BluetoothServer(Puplet parent, String friendlyNameStr) {
		this.parent = parent;

		// create a special attribute with friendly name
		friendlyName = new DataElement(DataElement.STRING, friendlyNameStr);

		// create thread to avoid deadlock
		accepterThread = new Thread(this);
		accepterThread.start();
	}

	/**
	 * Accepts a client and sends clients text to parent
	 * 
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		LocalDevice localDevice = null;
		try {
			localDevice = LocalDevice.getLocalDevice();

			// General Inquire Access Code.
			// The default inquiry code which is used to discover all devices in
			// range.
			localDevice.setDiscoverable(DiscoveryAgent.GIAC);
		} catch (BluetoothStateException bse) {
			System.err.println("Can not access the local bluetooth device.");
			bse.printStackTrace();
			return;
		}

		// Prepare a URL to create a notifier, please note that authorize is set
		// to false because some devices fail on authorize=true
		String url = new String("btl2cap://localhost:" + SERVER_UUID.toString()
				+ ";name=Bluetooth server;authorize=false");

		try {
			// Create a notifier
			connectionNotifier = (L2CAPConnectionNotifier) Connector.open(url
					.toString());
		} catch (IOException ioe) {
			System.err.println("Can not open a connection to localhost.");
			ioe.printStackTrace();
			return;
		}

		// Reference to telephone friendly name in use for phonecommunicator
		record = localDevice.getRecord(connectionNotifier);

		// Add friendly name dataElement to service record
		record.setAttributeValue(FRIENDLY_NAME_ATTRIBUTE_ID, friendlyName);

		// Client processor ensures that all incoming messages will be handled
		// and
		// that all incoming client request can be handled at the same time
		clientProcessor = new ClientProcessor();

		// Start accepting connections
		while (!isClosed) {
			L2CAPConnection conn = null;
			try {
				conn = connectionNotifier.acceptAndOpen();
			} catch (IOException e) {
				// Wrong client or interrupted
				continue;
			}
			clientProcessor.addConnection(conn);
		}
	}

	/**
	 * This method processes the client request
	 * 
	 * @param conn
	 *            the connection to process
	 */
	private synchronized void processClientConnection(L2CAPConnection conn) {
		try {
			// Check if connection ready
			if (conn.ready()) {
				byte[] frame = new byte[conn.getReceiveMTU()];
				int lengthMessage = conn.receive(frame);
				/*
				 * byte[] returnFrame = new byte[frame.length - 10];
				 * 
				 * String mobile = new String(frame, 1, 10);
				 * 
				 * // build return frame returnFrame[0] = frame[0]; for(int i =
				 * 1; i < returnFrame.length; i++) { returnFrame[i] = frame[i +
				 * 10]; }
				 */
				parent.frameEvent(frame);
			}
		} catch (IOException ioe) {
			System.err.println("Connection error.");
			ioe.printStackTrace();
		}

		// Close connection
		try {
			conn.close();
		} catch (IOException ioe) {
			System.err.println("Can not close the connection.");
			ioe.printStackTrace();
		}
	}

	/**
	 * Exits the accepting thread and closes the notifier.
	 */
	public void destroy() {
		// Causes accepterThread to end while loop
		isClosed = true;

		// Finalize server work
		if (connectionNotifier != null) {
			try {
				connectionNotifier.close();
			} catch (IOException ioe) {
				System.err.println("Can not close the connectionNotifier.");
				ioe.printStackTrace();
			}
		}

		// Wait until accepter thread is done
		try {
			accepterThread.join();
		} catch (InterruptedException ie) {
			System.err.println("Can not join the thread.");
			ie.printStackTrace();
		}

		// finalize the client processor
		if (clientProcessor != null) {
			clientProcessor.destroy();
		}
		clientProcessor = null;
	}

	/**
	 * This class is used for processing more then one client requests at a time
	 * 
	 * @author Elmar Keij, Richard Kettelerij, Nicolai Lourens, Tim Prijn,
	 *         Julien Rentrop
	 * 
	 * @version 0.1
	 */
	private class ClientProcessor implements Runnable {
		private Thread processorThread;
		private Vector queue = new Vector();
		private boolean isOk = true;

		public ClientProcessor() {
			processorThread = new Thread(this);
			processorThread.start();
		}

		public void run() {
			while (!isClosed) {
				// Semaphore for queue
				synchronized (this) {
					if (queue.size() == 0) {
						try {
							wait();
						} catch (InterruptedException ie) {
							System.err
									.println("Unable to wait fot this thread.");
							ie.printStackTrace();
							destroy();
							return;
						}
					}
				}

				// Get specified client connection and process its text
				L2CAPConnection conn;

				synchronized (this) {
					// In case server leaves.
					if (isClosed) {
						return;
					}
					conn = (L2CAPConnection) queue.firstElement();
					queue.removeElementAt(0);
					processClientConnection(conn);
				}
			}
		}

		// Adds the connection to queue and notifys the thread.
		public void addConnection(L2CAPConnection conn) {
			synchronized (this) {
				queue.addElement(conn);
				notify();
			}
		}

		// Closes the connections
		public void destroy() {
			L2CAPConnection conn;

			synchronized (this) {
				notify();

				while (queue.size() != 0) {
					conn = (L2CAPConnection) queue.firstElement();
					queue.removeElementAt(0);

					try {
						conn.close();
					} catch (IOException ioe) {
						System.err.println("Can not close the connection.");
						ioe.printStackTrace();
					}
				}
			}

			// Wait until this thread is done --> bool isClosed will be true
			// (see run())
			try {
				processorThread.join();
			} catch (InterruptedException ie) {
				System.err.println("Can not join the thread.");
				ie.printStackTrace();
			}
		}
	}

}

/**
 * This object represents the Graphical User Interface (GUI) of the
 * PhoneCommunicator MIDlet.
 * 
 * @version 0.1
 */
class PupletForm extends Form {
	private Hashtable images;

	private ImageItem currentImageItem;

	private Command exitCommand;

	private TextField sendText;
	private StringItem receiveText;

	/**
	 * Constructs an instance of this class
	 */
	public PupletForm(String s) {
		super(s);
		createGUIPictures();
		createGUIWidgets();
	}

	/**
	 * Create the user interface widgets (textboxes, commands, etc)
	 */
	private void createGUIWidgets() {
		// image field
		currentImageItem = new ImageItem("", getGUIPicture("normal"),
				ImageItem.LAYOUT_CENTER | ImageItem.LAYOUT_NEWLINE_BEFORE
						| ImageItem.LAYOUT_NEWLINE_AFTER, "");

		// textfields
		sendText = new TextField("", "", 25, TextField.ANY);
		receiveText = new StringItem("", "");

		// mainform
		this.append(receiveText);
		this.append(currentImageItem);
		this.append(sendText);

		// exit
		exitCommand = new Command("Exit", Command.EXIT, 1);
		this.addCommand(exitCommand);
	}

	/**
	 * Create the avatar pictures
	 */
	private Image getGUIPicture(String name) {
		if (images.containsKey(name)) {
			return (Image) images.get(name);
		} else {
			try {
				Image i = Image.createImage("/" + name + ".png");
				images.put(name, i);
				return i;
			} catch (IOException e) {
				System.err.println("Unable to load image " + name);
				e.printStackTrace();
				return null;
			}
		}
	}

	private void createGUIPictures() {
		images = new Hashtable();
		getGUIPicture("normal");
		getGUIPicture("happy");
		getGUIPicture("sad");
		getGUIPicture("angry");
		getGUIPicture("sick");
		getGUIPicture("sleepy");
	}

	/**
	 * Change the avatar that is display on the screen
	 * 
	 * @param image
	 *            the name of the imagetype, valid names are: "normal", "happy",
	 *            "sad", "angry", "sick" and "sleepy"
	 */
	public void setImage(String image) {
		Image i = getGUIPicture(image);
		if (i != null) {
			currentImageItem.setImage(i);
		}
	}

	/**
	 * Set a text in the receive label
	 * 
	 * @param s
	 *            the message to display
	 */
	public void setMessage(String s) {
		receiveText.setText(s);
	}

	/**
	 * Get the text of the send textfield
	 * 
	 * @return the message in the textfield
	 */
	public String getMessage() {
		return sendText.getString();
	}

	/**
	 * Get the text of the send textfield
	 * 
	 * @return the message in the textfield
	 */
	public void emptyMessage() {
		sendText.setString("");
	}

	/**
	 * Set the name of the Puplet
	 * 
	 * @param name
	 *            the name of the Puplet
	 */
	public void setName(String name) {
		if (name.length() > 7) {
			Exception e = new Exception(
					"Name to long. Choose a shorter name (8 characters)");
			e.printStackTrace();
		} else {
			this.setTitle(name);
		}
	}

	/**
	 * Get the name of the Puplet
	 * 
	 * @return the name of the Puplet
	 */
	public String getName() {
		return this.getTitle();
	}

	/**
	 * @return Returns the exitCommand.
	 */
	public Command getExitCommand() {
		return exitCommand;
	}

}

/**
 * This object represents the main 'interface' for Puplet by providing all the
 * needed methods and attributes
 * 
 */
public class Puplet extends MIDlet {
	/** The total length of a frame */
	public static final int FRAME_LENGTH = 32;

	/** The lenght of the name fields in a frame */
	private static final int NAME_LENGTH = 8;

	private PupletForm mainForm;
	private Display display;
	private Vector serviceRecords;
	private BluetoothServer bluetoothServer;
	private BluetoothClient bluetoothClient;
	private PupletCommandListener commandListener;
	private PupletTimer pupletTimer;
	private PupletPlayer player;
	private Command[] commandList;
	private long result = 0;

	/**
	 * Constructs an instance of this class
	 */
	public Puplet() {
		// create user interface
		display = Display.getDisplay(this);
		mainForm = new PupletForm("Puplet");
		pupletTimer = new PupletTimer();
		player = new PupletPlayer();
		commandListener = new PupletCommandListener();
		mainForm.setCommandListener(commandListener);
	}

	public Puplet(String name) {
		// create user interface
		display = Display.getDisplay(this);
		mainForm = new PupletForm(name);
		pupletTimer = new PupletTimer();
		player = new PupletPlayer();
		commandListener = new PupletCommandListener();
		mainForm.setCommandListener(commandListener);
	}

	/**
	 * Main entry point of the MIDP application
	 * 
	 * @see javax.microedition.midlet.MIDlet#startApp()
	 */
	protected void startApp() throws MIDletStateChangeException {
		// start Bluetooth server
		bluetoothServer = new BluetoothServer(this, getName());
		// start Bluetooth client
		bluetoothClient = new BluetoothClient();
		display.setCurrent(mainForm);
		pupletTimer.start();
	}

	/**
	 * Pauses the MIDP application
	 * 
	 * @see javax.microedition.midlet.MIDlet#pauseApp()
	 */
	protected void pauseApp() {
		// empty
	}

	/**
	 * Exit and destroy the MIDP application
	 * 
	 * @see javax.microedition.midlet.MIDlet#destroyApp(boolean)
	 */
	protected void destroyApp(boolean arg0) throws MIDletStateChangeException {
		// destroy Bluetooth server
		bluetoothServer.destroy();
	}

	/**
	 * Create a menu with the given menuitem names
	 * 
	 * @param menuItems
	 *            the names of the menuitems
	 */
	protected void makeMenu(String[] menuItems) {
		commandList = new Command[menuItems.length];
		if (menuItems != null) {
			for (int i = 0; i < menuItems.length; i++) {
				Command cmd = new Command(menuItems[i], Command.ITEM, 2);
				mainForm.addCommand(cmd);
				commandList[i] = cmd;
			}
		} else {
			System.err.println("Empty menyItems array passed.");
		}
	}

	protected void clearMenu() {
		for (int i = 0; i < commandList.length; i++) {
			mainForm.removeCommand(commandList[i]);
		}
	}

	/**
	 * Search for all puplets in range and return a list with the names of the
	 * puplets found.
	 * 
	 * @return the names of the puplets that are discovered
	 */
	protected String[] discoverPuplets() {
		String[] results = null;
		setMessage("Discovering...");

		// quick 'n dirty hack to force GUI update
		display.setCurrent(null);
		display.setCurrent(mainForm);

		serviceRecords = bluetoothClient.discoverDevices();
		results = new String[serviceRecords.size()];

		ServiceRecord servRec = null;
		DataElement de = null;
		for (int i = 0; i < serviceRecords.size(); i++) {
			servRec = (ServiceRecord) serviceRecords.elementAt(i);
			de = servRec
					.getAttributeValue(BluetoothClient.FRIENDLY_NAME_ATTRIBUTE_ID);
			results[i] = de.getValue().toString();
		}

		setMessage("Discovery completed");
		return results;
	}

	/**
	 * Send (broadcast) a frame of 32 bytes.
	 * 
	 * @param frame
	 *            the frame data as an array of raw bytes
	 */
	protected void sendFrame(byte[] frame) {
		ServiceRecord servRec = null;
		DataElement de = null;

		if ((frame == null) || (frame.length != 32)) {
			Exception e = new Exception(
					"Null frame or frame of bad length passed.");
			e.printStackTrace();
		} else {
			// read service record and find name attribute
			for (int i = 0; i < serviceRecords.size(); i++) {
				servRec = (ServiceRecord) serviceRecords.elementAt(i);
				de = servRec
						.getAttributeValue(BluetoothClient.FRIENDLY_NAME_ATTRIBUTE_ID);

				String url = servRec.getConnectionURL(
						ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
				bluetoothClient.sendMessage(url, frame);
			}
		}
	}

	/**
	 * Set a text in the receive label.
	 * 
	 * @param s
	 *            the message to display
	 */
	protected void setMessage(String s) {
		mainForm.setMessage(s);
	}

	/**
	 * Empty the send message.
	 * 
	 * @param s
	 *            the message to display
	 */
	protected void emptyMessage() {
		mainForm.emptyMessage();
	}

	/**
	 * Get the text of the send textfield
	 * 
	 * @return the message in the textfield
	 */
	protected String getMessage() {
		return mainForm.getMessage();
	}

	/**
	 * Set the name of the Puplet
	 * 
	 * @param name
	 *            the name of the Puplet
	 */
	protected void setName(String name) {
		mainForm.setName(name);
	}

	/**
	 * Get the name of the Puplet
	 * 
	 * @return the name of the Puplet
	 */
	protected String getName() {
		return mainForm.getName();
	}

	/**
	 * Change the avatar that is display on the screen
	 * 
	 * @param image
	 *            the name of the imagetype, valid names are: "normal", "happy",
	 *            "sad", "angry", "sick" and "sleepy"
	 */
	protected void setImage(String image) {
		mainForm.setImage(image);
	}

	/**
	 * This event is triggered when a frame of data is recieved by the
	 * localdevice.
	 * 
	 * @param frame
	 *            the frame data as an array of raw bytes
	 */
	protected void frameEvent(byte[] frame) {

	}

	/**
	 * This event is triggered every 300 ms.
	 */
	protected void timeEvent() {

	}

	/**
	 * This event is triggered when an item in the phone menu is clicked
	 * 
	 * @param menuItem
	 *            the name of the item that is clicked
	 */
	protected void menuEvent(String menuItem) {

	}

	/**
	 * This method converts an Array if ints into a Hex-String
	 * 
	 * @param result
	 *            The long integer with right guesses
	 * @return String The String
	 */
	protected String toHexString(long result) {
		return player.toHexString(result);
	}

	/**
	 * This method evaluates the antwoord String for the given bitposition
	 * 
	 * @param antwoord
	 *            The String containing the result
	 * @param index
	 *            The queried bitposition (starting at 0
	 * @return true if the bit is up false if the bit is down
	 */
	protected boolean letterOK(String antwoord, int index) {
		return player.letterOK(antwoord, index);
	}

	/**
	 * This method changes the bit in position 'índ' to 1 in the log 'result'
	 * 
	 * @param result
	 *            the long value that should be changed
	 * @param ind
	 *            The position of the bit that should be changed (starting at 0)
	 * @return the changed result
	 */
	protected long setUpPosition(long result, int ind) {
		return player.setUpPosition(result, ind);
	}

	class PupletCommandListener implements CommandListener {

		public void commandAction(Command cmd, Displayable disp) {
			if (cmd.equals(mainForm.getExitCommand())) {
				try {
					destroyApp(false);
					notifyDestroyed();
				} catch (MIDletStateChangeException e) {
					System.err.println("Unable to exit application");
					e.printStackTrace();
				}
			} else {
				menuEvent(cmd.getLabel());
			}
		}
	}

	class PupletTimer extends Thread implements Runnable {
		boolean running;

		public PupletTimer() {
			super();
			running = true;
		}

		public void run() {
			try {
				while (running) {
					sleep(300);
					timeEvent();
				}
			} catch (InterruptedException e) {
				System.err.println("Timer interrupted.");
				e.printStackTrace();
			}
		}
	}

}

class PupletPlayer {
	long resultaat;

	public PupletPlayer() {
		resultaat = 0;
	}

	/**
	 * This method converts an Array if ints into a Hex-String
	 * 
	 * @param result
	 *            The long integer with right guesses
	 * @return String The String
	 */

	protected String toHexString(long result) {
		return Long.toString(result, 16);
	}

	/**
	 * This method evaluates the antwoord String for the given bitposition
	 * 
	 * @param antwoord
	 *            The String containing the result
	 * @param index
	 *            The queried bitposition (starting at 0
	 * @return true if the bit is up false if the bit is down
	 */

	protected boolean letterOK(String antwoord, int index) {
		return ((Long.parseLong(antwoord, 16) & (macht(2, index))) > 0);
	}

	protected long setUpPosition(long result, int ind) {
		return result + macht(2, ind);
	}

	public static long macht(long a, long b) {
		long retval = 0;
		if (b == 0) {
			retval = 1;
		} else {
			retval = a;
			for (long i = 2; i <= b; i++) {
				retval = retval * a;
			}
		}
		return retval;
	}

}
