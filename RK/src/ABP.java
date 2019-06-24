import java.util.concurrent.atomic.AtomicInteger;

import fau.cs7.nwemu.*;

public class ABP {

	public static void main(String... args) {

		abstract class CommonHost extends AbstractHost {

			int seqnum = 0;

			/*
			 * Calculates the checksum for a NWEmu package
			 */
			protected int calculateChecksum(NWEmuPkt pkt) {
				int sum = pkt.seqnum;
				sum += pkt.acknum;
				sum += pkt.flags;

				for (byte b : pkt.payload) {
					sum += b;
				}

				return sum;
			}

			/*
			 * Takes a NWEmu package, calculates its checksum and compares it with its
			 * checksum. Returns false, if the numbers are equal and no bit-error occurred,
			 * otherwise true
			 */
			protected boolean hasBiterror(NWEmuPkt pkt) {
				return calculateChecksum(pkt) == pkt.checksum ? false : true;
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
				// Checks if there is an ongoing sending process
				if (state.compareAndSet(waitForData, waitForACK) == false) {
					return Boolean.FALSE;
				}

				// Building a new NWmu package
				NWEmuPkt pkt = new NWEmuPkt();
				pkt.seqnum = this.seqnum;

				for (int i = 0; i < NWEmu.PAYSIZE; ++i) {
					pkt.payload[i] = message.data[i];
				}

				pkt.checksum = calculateChecksum(pkt);

				// Update the current Package in case the package has to be sent again
				currPackage = pkt;

				// Send the package and start the timer
				toLayer3(pkt);
				startTimer(timerValue);

				return Boolean.TRUE;
			}

			@Override
			public synchronized void input(NWEmuPkt packet) {

				// Check if package is a delayed ACK and ignore it if so
				if (state.get() == waitForData) {
					return;
				}

				// Check for bit-errors and for the right ACK number
				if (hasBiterror(packet) || packet.acknum != this.seqnum) {
					return;
				}

				// No error -> stop timer, increment the sequence number and update state
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
				// Send the current package again
				toLayer3(currPackage);
				startTimer(timerValue);
			}
		}

		class ReceivingHost extends CommonHost {

			NWEmuPkt lastACK; // Stores the last ACK in case it has to be sent again

			@Override
			public void init() {
				// TODO
			}

			@Override
			public synchronized void input(NWEmuPkt packet) {

				// Check for bit-errors and the right sequence number
				if (!hasBiterror(packet) && packet.seqnum == this.seqnum) {

					// Extract the message from the package and deliver it to the upper layer
					NWEmuMsg message = new NWEmuMsg();

					for (int i = 0; i < NWEmu.PAYSIZE; ++i) {
						message.data[i] = packet.payload[i];
					}

					toLayer5(message);

					// Building the package for the ACK answer, send it and increment the sequence
					// number
					lastACK = new NWEmuPkt();
					lastACK.acknum = packet.seqnum;
					lastACK.checksum = calculateChecksum(lastACK);
					toLayer3(lastACK);
					seqnum = (seqnum + 1) % 2;

				} else if (packet.seqnum != seqnum) {

					// In case the sender sent a package that has already been received, send the
					// last ACK package again
					if (lastACK != null) {
						toLayer3(lastACK);
					}
				}
			}
		}

		// Main

		SendingHost HostA = new SendingHost();
		ReceivingHost HostB = new ReceivingHost();

		NWEmu TestEmu = new NWEmu(HostA, HostB);
		TestEmu.randTimer();
		TestEmu.emulate(20, 0.1, 0.2, 100.0, 1);
		// send 10 messages, no loss, no corruption, lambda 10, log level 1

	}

}
