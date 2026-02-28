package com.timmy.job;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;

import com.timmy.entity.Device;
import com.timmy.entity.DeviceStatus;
import com.timmy.entity.MachineCommand;
import com.timmy.mapper.DeviceMapper;
import com.timmy.mapper.MachineCommandMapper;
import com.timmy.websocket.WebSocketPool;

public class SendOrderJob extends Thread {

	// Relay-only mode: block background retries for backup and stale opendoor commands.
	private static final Set<String> BLOCKED_SYNC_COMMANDS = new HashSet<String>(
			Arrays.asList("getuserinfo", "getuserlist", "opendoor"));

	@Autowired
	MachineCommandMapper machineCommandMapper;

	@Autowired
	DeviceMapper deviceMapper;

	Map<String, DeviceStatus> wdList = WebSocketPool.wsDevice;

	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicBoolean startedOnce = new AtomicBoolean(false);

	public synchronized void startThread() {
		if (running.get()) {
			return;
		}
		running.set(true);
		if (startedOnce.compareAndSet(false, true)) {
			super.start();
		}
	}

	public synchronized void stopThread() {
		running.set(false);
		this.interrupt();
	}

	@Override
	public void run() {
		while (running.get() && !Thread.currentThread().isInterrupted()) {
			Iterator<Entry<String, DeviceStatus>> entries = wdList.entrySet().iterator();
			try {
				while (entries.hasNext()) {
					Entry<String, DeviceStatus> entry = entries.next();
					List<MachineCommand> inSending = machineCommandMapper.findPendingCommand(0, entry.getKey());

					if (inSending.size() > 0) {
						MachineCommand nextToSend = pickNextCommand(inSending);
						if (nextToSend == null) {
							continue;
						}

						List<MachineCommand> pendingCommand = machineCommandMapper.findPendingCommand(1, entry.getKey());
						if (pendingCommand.size() <= 0) {
							if (entry.getValue() != null && entry.getValue().getWebSocket() != null) {
								entry.getValue().getWebSocket().send(nextToSend.getContent());
								machineCommandMapper.updateCommandStatus(0, 1, new Date(), nextToSend.getId());
							}
						} else {
							MachineCommand pendingToRetry = pickPendingRetry(pendingCommand);
							retryPendingIfTimedOut(entry, pendingToRetry);
						}
					} else {
						List<MachineCommand> pendingCommand = machineCommandMapper.findPendingCommand(1, entry.getKey());
						if (pendingCommand.size() != 0) {
							MachineCommand pendingToRetry = pickPendingRetry(pendingCommand);
							retryPendingIfTimedOut(entry, pendingToRetry);
						}
					}
				}
			} catch (Exception e) {
				// keep polling
			}

			try {
				Thread.sleep(100L);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void retryPendingIfTimedOut(Entry<String, DeviceStatus> entry, MachineCommand pendingToRetry) {
		if (pendingToRetry == null || pendingToRetry.getRunTime() == null) {
			return;
		}
		if (System.currentTimeMillis() - (pendingToRetry.getRunTime()).getTime() <= 20 * 1000) {
			return;
		}
		if (pendingToRetry.getErrCount() < 3) {
			pendingToRetry.setErrCount(pendingToRetry.getErrCount() + 1);
			pendingToRetry.setRunTime(new Date());
			machineCommandMapper.updateByPrimaryKey(pendingToRetry);
			Device device = deviceMapper.selectDeviceBySerialNum(pendingToRetry.getSerial());
			if (device != null && device.getStatus() != 0 && entry.getValue() != null
					&& entry.getValue().getWebSocket() != null) {
				entry.getValue().getWebSocket().send(pendingToRetry.getContent());
			}
		} else {
			pendingToRetry.setErrCount(pendingToRetry.getErrCount() + 1);
			machineCommandMapper.updateByPrimaryKey(pendingToRetry);
		}
	}

	private MachineCommand pickNextCommand(List<MachineCommand> inSending) {
		if (inSending == null || inSending.isEmpty()) {
			return null;
		}

		MachineCommand firstNormal = null;
		for (int i = 0; i < inSending.size(); i++) {
			MachineCommand cmd = inSending.get(i);
			if (cmd == null) {
				continue;
			}
			if (isBlockedSyncCommand(cmd.getName())) {
				markCommandSkipped(cmd);
				continue;
			}
			if (firstNormal == null) {
				firstNormal = cmd;
			}
		}
		return firstNormal;
	}

	private MachineCommand pickPendingRetry(List<MachineCommand> pendingCommands) {
		if (pendingCommands == null || pendingCommands.isEmpty()) {
			return null;
		}
		for (int i = 0; i < pendingCommands.size(); i++) {
			MachineCommand cmd = pendingCommands.get(i);
			if (cmd == null) {
				continue;
			}
			if (isBlockedSyncCommand(cmd.getName())) {
				markCommandSkipped(cmd);
				continue;
			}
			return cmd;
		}
		return null;
	}

	private boolean isBlockedSyncCommand(String commandName) {
		return commandName != null && BLOCKED_SYNC_COMMANDS.contains(commandName);
	}

	private void markCommandSkipped(MachineCommand command) {
		if (command == null || command.getId() == null) {
			return;
		}
		machineCommandMapper.updateCommandStatus(1, 1, new Date(), command.getId());
	}
}
