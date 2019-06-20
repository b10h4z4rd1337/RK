import fau.cs7.nwemu.*;

public class ABP {

	public static void main(String... args) {

		class CommonHost extends AbstractHost {

			int seqnum = 0;
			int acknum = 0;
		}

		class SendingHost extends CommonHost {

			@Override
			public Boolean output(NWEmuMsg message) {
				// TODO
				return Boolean.FALSE;
			}

			@Override
			public void input(NWEmuPkt packet) {
				// TODO
			}

			@Override
			public void init() {
				// TODO
			}

			@Override
			public void timerInterrupt() {
				// TODO
			}
		}

		class ReceivingHost extends CommonHost {

			@Override
			public void init() {
				// TODO
			}
			
			@Override
			public void input(NWEmuPkt packet) {
				// TODO
			}
		}

		// Main

		SendingHost HostA = new SendingHost();
		ReceivingHost HostB = new ReceivingHost();

		NWEmu TestEmu = new NWEmu(HostA, HostB);
		TestEmu.randTimer();
		TestEmu.emulate(10, 0.0, 0.0, 10.0, 1);
		// send 10 messages, no loss, no corruption, lambda 10, log level 1

	}

}
