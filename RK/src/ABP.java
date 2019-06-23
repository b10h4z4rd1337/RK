import java.util.concurrent.atomic.AtomicInteger;

import fau.cs7.nwemu.*;

public class ABP {

	public static void main(String... args) {

		class CommonHost extends AbstractHost {

			int seqnum = 0;

			protected int buildChecksum(NWEmuPkt pkt) {
				int sum = pkt.seqnum;
				sum += pkt.acknum;
				sum += pkt.flags;

				for (byte b : pkt.payload) {
					sum += b;
				}

				return sum;
			}

			protected boolean hasBiterror(NWEmuPkt pkt) {
				return buildChecksum(pkt) == pkt.checksum ? false : true;
			}
		}

		class SendingHost extends CommonHost {

			private final int waitForData = 0;
			private final int waitForACK = 1;
			private final double timerValue = 20.0; // Time to wait until a package is send again

			private AtomicInteger state = new AtomicInteger(waitForData);
			private NWEmuPkt currPackage;

			@Override
			public Boolean output(NWEmuMsg message) {
				// TODO
				// Checks if there is an ongoing sending process
				if (state.compareAndSet(waitForData, waitForACK) == false) {
					return Boolean.FALSE;
				}

				NWEmuPkt pkt = new NWEmuPkt();
				pkt.seqnum = this.seqnum;

				for (int i = 0; i < NWEmu.PAYSIZE; ++i) {
					pkt.payload[i] = message.data[i];
				}
				
				pkt.checksum = buildChecksum(pkt);

				currPackage = pkt;
				toLayer3(pkt);
				startTimer(timerValue);

				return Boolean.TRUE;
			}

			@Override
			public void input(NWEmuPkt packet) {
				// TODO: check for transfer errors
				if (state.get() == waitForData) {
					return;
				}

				if (hasBiterror(packet) || packet.acknum != this.seqnum) {
					return;
				}

				stopTimer();
				seqnum = (seqnum + 1) % 2;
				state.set(waitForData);

			}

			@Override
			public void init() {
				// TODO
			}

			@Override
			public void timerInterrupt() {
				toLayer3(currPackage);
				startTimer(timerValue);
			}
		}

		class ReceivingHost extends CommonHost {

			NWEmuPkt lastACK = new NWEmuPkt();

			@Override
			public void init() {
				// TODO
			}

			@Override
			public void input(NWEmuPkt packet) {
				// TODO
				if (hasBiterror(packet) || packet.seqnum != this.seqnum) {
					toLayer3(lastACK);
				}

				NWEmuMsg message = new NWEmuMsg();

				for (int i = 0; i < NWEmu.PAYSIZE; ++i) {
					message.data[i] = packet.payload[i];
				}

				toLayer5(message);

				lastACK.acknum = this.seqnum;
				lastACK.checksum = this.buildChecksum(lastACK);
				toLayer3(lastACK);
				seqnum = (seqnum + 1) % 2;
			}
		}

		// Main

		SendingHost HostA = new SendingHost();
		ReceivingHost HostB = new ReceivingHost();

		NWEmu TestEmu = new NWEmu(HostA, HostB);
		TestEmu.randTimer();
		TestEmu.emulate(50, 0.1, 0.2, 100.0, 1);
		// send 10 messages, no loss, no corruption, lambda 10, log level 1

	}

}
