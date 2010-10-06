package puplets;
import puplet.Puplet;

public class Michiel extends Puplet {
	private String[] menu = {"Discover","Stel woord in","Geef hint"};
	private int counter;
	
	char[] woordinstukjes;
	private String puntjes="";
	private String bericht;
	private char berichtletter;
	private boolean lettergeraden=false;
	private String woord;
	protected String[] puplets;
	protected byte[] frame = new byte[32];
	private String opponent = null;

	public String getOpponent() {
		return opponent;
	}

	public void setOpponent(String opponent) {
		this.opponent = opponent;
	}
	public void setFrameReceiver(String s) {
		copyStringToFrame(s, 7, 7);
	}

	public void setFrameSender(String s) {
		copyStringToFrame(s, 0, 7);
	}

	public void setFrameSenderType(String s) {
		copyStringToFrame(s, 15, 3);
	}

	public void copyStringToFrame(String s, int dest, int length) {
		byte[] b = s.getBytes();
		for (int i = 0; i < length; i++) {
			if (i < b.length) {
				frame[dest + i] = b[i];
			}
		}
	}

	// building of frames
	public void emptyFrame() {
		for (int i = 0; i < 32; i++)
			frame[i] = 0;
	}
	
	//constructor 
	public Michiel(){
		super("Baas Michiel");
		makeMenu(menu);
	}
	public void timeEvent(){
		counter=counter+1;
		if(counter>5){
			counter=0;
		}
	}
	public void frameEvent(byte[] frame) {
		String sender_name = new String(frame, 0, 7).trim();
		String rec_name = new String(frame, 7, 7).trim();
		String boodschap = new String(frame, 20, 15).trim();
		if (rec_name.equalsIgnoreCase(getName())) {
			if (frame[15] == 'R' && frame[16]== 'L') {
				berichtletter = bericht.charAt(0);
				for(int i=0;i<woordinstukjes.length;i++){
					if(woordinstukjes[i] == berichtletter){
						puntjes = puntjes+berichtletter;
						lettergeraden = true;
					}else{
						puntjes = puntjes+".";
					}
				}
				stuurBerichtje(puntjes,'R','L','R','1');
			}
		}
	}
	public void stuurBerichtje(String message,char type1,char type2,char type3,char type4){
		emptyFrame();
		setFrameSender(getName());
		setFrameReceiver(puplets[0]);
		frame[15] = (byte)type1;
		frame[16] = (byte)type2;
		frame[17] = (byte)type3;
		frame[18] = (byte)type4;
		copyStringToFrame(message, 19, 10);
		sendFrame(frame);
	}
	public void setRaadwoord(String woord){
		woordinstukjes = new char[woord.length()];
		for(int i=0;i<woord.length();i++){
			woordinstukjes[i] = woord.charAt(i);
			puntjes = puntjes+".";
		}
		stuurBerichtje(puntjes,'W','P','0','0');
	}
	public void menuEvent(String s){
		int menukeuze=0;
		for(menukeuze=0;menukeuze<menu.length;menukeuze++){
			if(menu[menukeuze]==s){
				break;
			}
		}
		switch(menukeuze){
			case 0: {
				setMessage("Discovering...");
				puplets = discoverPuplets();
				setMessage("Discovered " + puplets.length);
				break;
			}
			case 1:
				setMessage("Geef woord in:");
				woord = getMessage();
				setRaadwoord(woord);
			break;
			case 2:

			break;
		}
	}
}
