import fau.cs7.nwemu.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

public class GoBackN {

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

			private final double TIMEOUT = 20.0; // Time to wait until a package is send again
			private final int WINDOW_SIZE = 8; //Our "Fenstergröße"

			private LinkedList<NWEmuPkt> buffer = new LinkedList<>();
			private NWEmuPkt[] outputBuffer = new NWEmuPkt[WINDOW_SIZE];
			private AtomicInteger paketsOnNetwork = new AtomicInteger(0);

			private int lastACK = -1;

			@Override
			public Boolean output(NWEmuMsg message) {

				// Building a new NWmu package
				NWEmuPkt pkt = new NWEmuPkt();
				pkt.seqnum = this.seqnum;
				System.arraycopy(message.data, 0, pkt.payload, 0, message.data.length);
				// Set flag to current time to resend in appropriate time
				pkt.flags = (int) System.currentTimeMillis();
				pkt.checksum = calculateChecksum(pkt);

				if (paketsOnNetwork.get() == WINDOW_SIZE) {
					// Maximum on network hit, store to buffer
					buffer.add(pkt);
				} else {
					// Resources on network available, send
					// Send the package and start the timer
					outputBuffer[seqnum % WINDOW_SIZE] = pkt;
					toLayer3(pkt);

					if (this.paketsOnNetwork.getAndIncrement() == 0) {
						startTimer(TIMEOUT);
					}
				}

				this.seqnum++;
				return Boolean.TRUE;
			}

			@Override
			public synchronized void input(NWEmuPkt packet) {
				int currentACK = packet.acknum;

				// Check for bit-errors and for the right ACK number
				if (hasBiterror(packet) && packet.acknum > lastACK) {
					return;
				}

				// Remove acknowledged packets from output buffer
				// Send buffered packets if available
				// Does nothing if ACK is repeated
				for (int i = this.lastACK + 1; i <= currentACK; i++) {
					int pos = i % WINDOW_SIZE;
					NWEmuPkt pkt;
					try {
						pkt = buffer.pop();

						toLayer3(pkt);
						this.seqnum++;

						if (this.paketsOnNetwork.get() == 0) {
							startTimer(TIMEOUT);
						}
					} catch (NoSuchElementException e) {
						pkt = null;
						paketsOnNetwork.decrementAndGet();
					}
					outputBuffer[pos] = pkt;
				}

				this.lastACK = currentACK;

				if (paketsOnNetwork.get() == 0) {
					// No error -> stop timer
					stopTimer();
				}
			}

			@Override
			public void init() { }

			@Override
			public void timerInterrupt() {
				// Send all packets in output-buffer again
				int currentTime = (int) System.currentTimeMillis();
				for (NWEmuPkt pkt : outputBuffer) {
					if (pkt != null) {
						if (pkt.flags <= currentTime + TIMEOUT) {
							toLayer3(pkt);
						}
					}
				}
				startTimer(TIMEOUT);
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
				if (!hasBiterror(packet) && packet.seqnum == seqnum) {

					// Extract the message from the package and deliver it to the upper layer
					NWEmuMsg message = new NWEmuMsg();
					System.arraycopy(packet.payload, 0, message.data, 0, packet.payload.length);
					toLayer5(message);

					// Building the package for the ACK answer, send it and increment the sequence
					// number
					lastACK = new NWEmuPkt();
					lastACK.acknum = packet.seqnum;
					lastACK.checksum = calculateChecksum(lastACK);
					toLayer3(lastACK);
					seqnum = packet.seqnum + 1;

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
		TestEmu.emulate(20, 0.2, 0.2, 10.0, 2);
		// send 10 messages, no loss, no corruption, lambda 10, log level 1

	}

}
