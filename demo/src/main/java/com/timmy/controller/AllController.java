package com.timmy.controller;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.timmy.entity.Device;
import com.timmy.entity.EnrollInfo;
import com.timmy.entity.MachineCommand;
import com.timmy.entity.Msg;
import com.timmy.entity.Person;
import com.timmy.entity.PersonTemp;
import com.timmy.entity.Records;
import com.timmy.entity.UserInfo;
import com.timmy.util.ControllerBase;
import com.timmy.util.ImageProcess;
import com.timmy.websocket.WebSocketPool;

@Controller

public class AllController extends ControllerBase {

	/*
	 * @Autowired EnrollInfoService enrollInfoService;
	 */
	private static final String PERSON_PHOTO_DIR = "C:/dynamicface/picture/";
	private static final int PASSWORD_MAX_LENGTH = 10;
	private static final int CARD_MAX_LENGTH = 20;
	private static final String SYNC_TARGET_ALL = "all";
	// Relay-only mode: block backup/user sync APIs to prevent repeated setuserinfo traffic.
	private static final boolean BACKUP_SYNC_ENABLED = false;

	@RequestMapping("/hello1")
	public String hello() {
		return "hello";
	}

	/* èŽ·å–æ‰€æœ‰è€ƒå‹¤æœº */
	@ResponseBody
	@RequestMapping(value = "/device", method = RequestMethod.GET)
	public Msg getAllDevice() {
		List<Device> deviceList = deviceService.findAllDevice();
		return Msg.success().add("device", deviceList);
	}

	/* èŽ·å–æ‰€æœ‰è€ƒå‹¤æœº */
	@ResponseBody
	@RequestMapping(value = "/enrollInfo", method = RequestMethod.GET)
	public Msg getAllEnrollInfo() {
		List<Person> enrollInfo = personService.selectAll();

		return Msg.success().add("enrollInfo", enrollInfo);
	}

	/* é‡‡é›†æ‰€æœ‰çš„ç”¨æˆ· */
	@ResponseBody
	@RequestMapping(value = "/sendWs", method = RequestMethod.GET)
	public Msg sendWs(@RequestParam("deviceSn") String deviceSn) {
		if (!BACKUP_SYNC_ENABLED) {
			return Msg.fail().add("error", "Backup sync is disabled in relay-only mode.");
		}
		String message = "{\"cmd\":\"getuserlist\",\"stn\":true}";

		System.out.println("sss" + deviceSn);

		// WebSocketPool.sendMessageToDeviceStatus(deviceSn, message);
		List<Device> deviceList = deviceService.findAllDevice();
		for (int i = 0; i < deviceList.size(); i++) {
			MachineCommand machineCommand = new MachineCommand();
			machineCommand.setContent(message);
			machineCommand.setName("getuserlist");
			machineCommand.setStatus(0);
			machineCommand.setSendStatus(0);
			machineCommand.setErrCount(0);
			machineCommand.setSerial(deviceList.get(i).getSerialNum());
			machineCommand.setGmtCrate(new Date());
			machineCommand.setGmtModified(new Date());
			machineCommand.setContent(message);
			machineComandService.addMachineCommand(machineCommand);
		}

		return Msg.success();
	}

	@ResponseBody
	@RequestMapping(value = "addPerson", method = RequestMethod.POST)
	public Msg addPerson(PersonTemp personTemp, @RequestParam(value = "pic", required = false) MultipartFile pic) throws Exception {
		if (personTemp != null && personTemp.getUserId() != null) {
			upsertPersonInClientDb(personTemp, pic);
			return Msg.success();
		}

		String path = "C:/dynamicface/picture/";
		System.out.println("å›¾ç‰‡çœŸå®žè·¯å¾„" + path);
		System.out.println("æ–°å¢žäººå‘˜ä¿¡æ¯===================" + personTemp);
		String photoName = "";
		String newName = "";
		// EnrollInfo enrollInfo=new EnrollInfo();
		if (pic != null) {
			if (pic.getOriginalFilename() != null && !("").equals(pic.getOriginalFilename())) {
				photoName = pic.getOriginalFilename();
				newName = UUID.randomUUID().toString() + photoName.substring(photoName.lastIndexOf("."));
				File photoFile = new File(path, newName);
				if (!photoFile.exists()) {
					photoFile.mkdirs();
				}
				pic.transferTo(photoFile);

			}

		}
		Person person = new Person();
		person.setId(personTemp.getUserId());
		person.setName(personTemp.getName());
		person.setRollId(personTemp.getPrivilege());
		Person person2 = personService.selectByPrimaryKey(personTemp.getUserId());
		if (person2 == null) {
			personService.insert(person);
		}
		if (personTemp.getPassword() != null && !personTemp.getPassword().equals("")) {
			EnrollInfo enrollInfoTemp2 = new EnrollInfo();
			enrollInfoTemp2.setBackupnum(10);
			enrollInfoTemp2.setEnrollId(personTemp.getUserId());
			enrollInfoTemp2.setSignatures(personTemp.getPassword());
			enrollInfoService.insertSelective(enrollInfoTemp2);
		}
		if (personTemp.getCardNum() != null && !personTemp.getCardNum().equals("")) {
			EnrollInfo enrollInfoTemp3 = new EnrollInfo();
			enrollInfoTemp3.setBackupnum(11);
			enrollInfoTemp3.setEnrollId(personTemp.getUserId());
			enrollInfoTemp3.setSignatures(personTemp.getCardNum());
			enrollInfoService.insertSelective(enrollInfoTemp3);
		}

		if (newName != null && !newName.equals("")) {
			EnrollInfo enrollInfoTemp = new EnrollInfo();
			enrollInfoTemp.setBackupnum(50);
			enrollInfoTemp.setEnrollId(personTemp.getUserId());
			String base64Str = ImageProcess.imageToBase64Str("C:/dynamicface/picture/" + newName);
			enrollInfoTemp.setImagePath(newName);
			enrollInfoTemp.setSignatures(base64Str);
			System.out.println("å›¾ç‰‡æ•°æ®é•¿åº¦" + base64Str.length());
			enrollInfoService.insertSelective(enrollInfoTemp);
		}

		return Msg.success();

	}

	@ResponseBody
	@RequestMapping(value = "savePerson", method = RequestMethod.POST)
	public Msg savePerson(PersonTemp personTemp, @RequestParam(value = "pic", required = false) MultipartFile pic,
			@RequestParam(value = "deviceSn", required = false) String deviceSn,
			@RequestParam(value = "syncTarget", required = false) String syncTarget) {
		if (personTemp == null || personTemp.getUserId() == null) {
			return Msg.fail().add("error", "UserId is required.");
		}
		try {
			upsertPersonInClientDb(personTemp, pic);
			boolean syncQueued = queueUserSyncToDevice(personTemp, deviceSn, syncTarget);
			boolean hasFaceTemplate = hasFaceTemplate(personTemp.getUserId());
			boolean hasFaceData = hasFaceData(personTemp.getUserId());
			return Msg.success().add("syncQueued", syncQueued).add("hasFaceTemplate", hasFaceTemplate)
					.add("hasFaceData", hasFaceData);
		} catch (Exception ex) {
			ex.printStackTrace();
			String detail = ex.getMessage();
			if (detail == null || detail.trim().isEmpty()) {
				detail = ex.getClass().getSimpleName();
			}
			return Msg.fail().add("error", detail);
		}
	}

	@ResponseBody
	@RequestMapping(value = "personDetail", method = RequestMethod.GET)
	public Msg personDetail(@RequestParam("enrollId") Long enrollId) {
		if (enrollId == null) {
			return Msg.fail().add("error", "enrollId is required.");
		}
		Person person = personService.selectByPrimaryKey(enrollId);
		if (person == null) {
			return Msg.fail().add("error", "User not found.");
		}
		PersonTemp personTemp = new PersonTemp();
		personTemp.setUserId(person.getId());
		personTemp.setName(person.getName());
		personTemp.setPrivilege(person.getRollId() == null ? 0 : person.getRollId());

		EnrollInfo passwordInfo = enrollInfoService.selectByBackupnum(enrollId, 10);
		if (passwordInfo != null && hasText(passwordInfo.getSignatures())) {
			personTemp.setPassword(truncate(normalizeText(passwordInfo.getSignatures()), PASSWORD_MAX_LENGTH));
		}
		EnrollInfo cardInfo = enrollInfoService.selectByBackupnum(enrollId, 11);
		if (cardInfo != null && hasText(cardInfo.getSignatures())) {
			personTemp.setCardNum(truncate(normalizeText(cardInfo.getSignatures()), CARD_MAX_LENGTH));
		}

		return Msg.success().add("person", personTemp);
	}

	private void upsertPersonInClientDb(PersonTemp personTemp, MultipartFile pic) throws Exception {
		Person person = new Person();
		person.setId(personTemp.getUserId());
		person.setName(personTemp.getName());
		person.setRollId(personTemp.getPrivilege());
		if (personService.selectByPrimaryKey(personTemp.getUserId()) == null) {
			personService.insert(person);
		} else {
			personService.updateByPrimaryKey(person);
		}

		upsertTextBackupWithFallback(personTemp.getPassword(), personTemp.getUserId(), 10,
				new int[] { PASSWORD_MAX_LENGTH, 8, 6, 4, 2, 1 });
		upsertTextBackupWithFallback(personTemp.getCardNum(), personTemp.getUserId(), 11,
				new int[] { CARD_MAX_LENGTH, 16, 12, 10, 8, 6, 4, 2, 1 });
		if (pic != null && hasText(pic.getOriginalFilename())) {
			String base64Photo = savePhotoAsBase64(pic);
			enrollInfoService.updateByEnrollIdAndBackupNum(base64Photo, personTemp.getUserId(), 50);
		}
	}

	private void upsertTextBackupWithFallback(String rawValue, Long userId, int backupNum, int[] maxLengths) {
		String normalized = normalizeText(rawValue);
		if (!hasText(normalized)) {
			return;
		}
		DataIntegrityViolationException lastException = null;
		for (int maxLength : maxLengths) {
			if (maxLength <= 0) {
				continue;
			}
			String attemptValue = truncate(normalized, maxLength);
			if (!hasText(attemptValue)) {
				continue;
			}
			try {
				enrollInfoService.updateByEnrollIdAndBackupNum(attemptValue, userId, backupNum);
				return;
			} catch (DataIntegrityViolationException ex) {
				lastException = ex;
			}
		}
		if (lastException != null) {
			throw lastException;
		}
	}

	private boolean queueUserSyncToDevice(PersonTemp personTemp, String deviceSn, String syncTarget) {
		Person persisted = personService.selectByPrimaryKey(personTemp.getUserId());
		if (persisted == null) {
			return false;
		}
		String name = hasText(persisted.getName()) ? persisted.getName() : String.valueOf(personTemp.getUserId());
		int admin = persisted.getRollId() == null ? 0 : persisted.getRollId();

		String normalizedTarget = normalizeText(syncTarget);
		if (SYNC_TARGET_ALL.equalsIgnoreCase(normalizedTarget)) {
			List<Device> devices = deviceService.findAllDevice();
			if (devices == null || devices.isEmpty()) {
				return false;
			}
			boolean queued = false;
			for (Device device : devices) {
				if (device == null || !hasText(device.getSerialNum())) {
					continue;
				}
				if (queueUserSyncToSingleDevice(personTemp.getUserId(), name, admin, device.getSerialNum().trim())) {
					queued = true;
				}
			}
			return queued;
		}

		if (!hasText(deviceSn)) {
			return false;
		}
		return queueUserSyncToSingleDevice(personTemp.getUserId(), name, admin, deviceSn.trim());
	}

	private boolean queueUserSyncToSingleDevice(Long userId, String name, int admin, String deviceSn) {
		// Latest-only sync for device recognition:
		// send name plus available face data for this single user only.
		personService.setUserToDevice(userId, name, -1, admin, "", deviceSn);
		queueFaceToDevice(userId, name, admin, deviceSn);
		return true;
	}

	private boolean queueFaceToDevice(Long userId, String name, int admin, String deviceSn) {
		boolean queued = false;
		for (int backupNum = 20; backupNum <= 27; backupNum++) {
			if (queueBackupToDevice(userId, name, admin, backupNum, deviceSn)) {
				queued = true;
			}
		}
		if (!queued) {
			queued = queueBackupToDevice(userId, name, admin, 50, deviceSn);
		}
		return queued;
	}

	private boolean queueBackupToDevice(Long userId, String name, int admin, int backupNum, String deviceSn) {
		EnrollInfo info = enrollInfoService.selectByBackupnum(userId, backupNum);
		if (info != null && hasText(info.getSignatures())) {
			personService.setUserToDevice(userId, name, backupNum, admin, info.getSignatures(), deviceSn);
			return true;
		}
		return false;
	}

	private boolean hasFaceTemplate(Long userId) {
		int[] faceBackupNums = new int[] { 20, 21, 22, 23, 24, 25, 26, 27 };
		for (int backupNum : faceBackupNums) {
			EnrollInfo info = enrollInfoService.selectByBackupnum(userId, backupNum);
			if (info != null && hasText(info.getSignatures())) {
				return true;
			}
		}
		return false;
	}

	private boolean hasFaceData(Long userId) {
		if (hasFaceTemplate(userId)) {
			return true;
		}
		EnrollInfo photoInfo = enrollInfoService.selectByBackupnum(userId, 50);
		return photoInfo != null && hasText(photoInfo.getSignatures());
	}

	private String savePhotoAsBase64(MultipartFile pic) throws Exception {
		String photoName = pic.getOriginalFilename();
		String extension = "";
		int suffixIndex = photoName.lastIndexOf(".");
		if (suffixIndex >= 0) {
			extension = photoName.substring(suffixIndex);
		}
		String savedFileName = UUID.randomUUID().toString() + extension;
		File photoDir = new File(PERSON_PHOTO_DIR);
		if (!photoDir.exists()) {
			photoDir.mkdirs();
		}
		File photoFile = new File(photoDir, savedFileName);
		pic.transferTo(photoFile);
		return ImageProcess.imageToBase64Str(photoFile.getAbsolutePath());
	}

	private String normalizeText(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private String truncate(String value, int maxLength) {
		if (value == null) {
			return null;
		}
		if (maxLength <= 0 || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	private boolean hasText(String value) {
		return value != null && !value.trim().isEmpty();
	}

	@ResponseBody
	@RequestMapping(value = "getUserInfo", method = RequestMethod.GET)
	public Msg getUserInfo(@RequestParam("deviceSn") String deviceSn) {
		System.out.println("è¿›å…¥controller");
		List<Person> person = personService.selectAll();
		List<EnrollInfo> enrollsPrepared = new ArrayList<EnrollInfo>();
		for (int i = 0; i < person.size(); i++) {
			Long enrollId2 = person.get(i).getId();
			List<EnrollInfo> enrollInfos = enrollInfoService.selectByEnrollId(enrollId2);
			for (int j = 0; j < enrollInfos.size(); j++) {
				if (enrollInfos.get(j).getEnrollId() != null && enrollInfos.get(j).getBackupnum() != null) {
					enrollsPrepared.add(enrollInfos.get(j));
				}
			}
		}
		System.out.println("é‡‡é›†ç”¨æˆ·æ•°æ®" + enrollsPrepared);
		personService.getSignature2(enrollsPrepared, deviceSn);

		return Msg.success();
	}

	/* èŽ·å–å•ä¸ªç”¨æˆ· */
	@ResponseBody
	@RequestMapping("sendGetUserInfo")
	public Msg sendGetUserInfo(@RequestParam("enrollId") int enrollId, @RequestParam("backupNum") int backupNum,
			@RequestParam("deviceSn") String deviceSn) {
		if (!isSupportedEnrollBackupNum(backupNum)) {
			return Msg.fail().add("error", "Only face(20-27), password(10), card(11), and photo(50) are supported.");
		}

		List<Device> deviceList = deviceService.findAllDevice();
		System.out.println("è®¾å¤‡ä¿¡æ¯" + deviceList);

		machineComandService.addGetOneUserCommand(enrollId, backupNum, deviceSn);

		return Msg.success();
	}

	/* ä¸‹å‘æ‰€æœ‰ç”¨æˆ·ï¼Œé¢å‘é€‰ä¸­è€ƒå‹¤æœº */
	@ResponseBody
	@RequestMapping(value = "/setPersonToDevice", method = RequestMethod.GET)
	public Msg sendSetUserInfo(@RequestParam("deviceSn") String deviceSn) {
		if (!BACKUP_SYNC_ENABLED) {
			return Msg.fail().add("error", "Backup sync is disabled in relay-only mode.");
		}

		personService.setUserToDevice2(deviceSn);
		return Msg.success();

	}

	@ResponseBody
	@RequestMapping(value = "setUsernameToDevice", method = RequestMethod.GET)
	public Msg setUsernameToDevice(@RequestParam("deviceSn") String deviceSn) {
		personService.setUsernameToDevice(deviceSn);
		return Msg.success();
	}

	@ResponseBody
	@RequestMapping(value = "/getDeviceInfo", method = RequestMethod.GET)
	public Msg getDeviceInfo(@RequestParam("deviceSn") String deviceSn) {
		String message = "{\"cmd\":\"getdevinfo\"}";

		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("getdevinfo");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);
		return Msg.success();
	}

	/* ä¸‹å‘å•ä¸ªç”¨æˆ·åˆ°æœºå™¨ï¼Œå¯¹é€‰ä¸­è€ƒå‹¤æœº */
	@ResponseBody
	@RequestMapping(value = "/setOneUser", method = RequestMethod.GET)
	public Msg setOneUserTo(@RequestParam("enrollId") Long enrollId, @RequestParam("backupNum") int backupNum,
			@RequestParam("deviceSn") String deviceSn) {
		if (!BACKUP_SYNC_ENABLED && backupNum != -1 && !isFaceBackupNum(backupNum)) {
			return Msg.fail().add("error",
					"Relay-only mode allows single-user face sync only (backup 20-27 or 50).");
		}
		if (!isSupportedSetBackupNum(backupNum)) {
			return Msg.fail().add("error",
					"Only name(-1), face(20-27), password(10), card(11), and photo(50) are supported.");
		}
		Person person = new Person();
		person = personService.selectByPrimaryKey(enrollId);
		EnrollInfo enrollInfo = new EnrollInfo();
		System.out.println("ba" + backupNum);
		enrollInfo = enrollInfoService.selectByBackupnum(enrollId, backupNum);
		if (enrollInfo != null) {
			personService.setUserToDevice(enrollId, person.getName(), backupNum, person.getRollId(),
					enrollInfo.getSignatures(), deviceSn);
			return Msg.success();
		} else if (backupNum == -1) {
			personService.setUserToDevice(enrollId, person.getName(), backupNum, 0, "", deviceSn);
			return Msg.success();
		} else {
			return Msg.fail();
		}

	}

	/* ä»Žè€ƒå‹¤æœºåˆ é™¤ç”¨æˆ· */
	@ResponseBody
	@RequestMapping(value = "/deletePersonFromDevice", method = RequestMethod.GET)
	public Msg deleteDeviceUserInfo(@RequestParam("enrollId") Long enrollId,
			@RequestParam("deviceSn") String deviceSn) {

		System.out.println("åˆ é™¤ç”¨æˆ·devicesn===================" + deviceSn);
		personService.deleteUserInfoFromDevice(enrollId, deviceSn);
		// personService.deleteByPrimaryKey(enrollId);
		return Msg.success();
	}

	/* åˆå§‹åŒ–è€ƒå‹¤æœº */
	@ResponseBody
	@RequestMapping(value = "/initSystem", method = RequestMethod.GET)
	public Msg initSystem(@RequestParam("deviceSn") String deviceSn) {
		System.out.println("åˆå§‹åŒ–è¯·æ±‚");
		String message = "{\"cmd\":\"enabledevice\"}";
		String message2 = "{\"cmd\":\"settime\",\"cloudtime\":\"2020-12-23 13:49:30\"}";
		String s4 = "{\"cmd\":\"settime\",\"cloudtime\":\"2016-03-25 13:49:30\"}";
		String s2 = "{\"cmd\":\"setdevinfo\",\"deviceid\":1,\"language\":0,\"volume\":0,\"screensaver\":0,\"verifymode\":0,\"sleep\":0,\"userfpnum\":3,\"loghint\":1000,\"reverifytime\":0}";
		String s3 = "{\"cmd\":\"setdevlock\",\"opendelay\":5,\"doorsensor\":0,\"alarmdelay\":0,\"threat\":0,\"InputAlarm\":0,\"antpass\":0,\"interlock\":0,\"mutiopen\":0,\"tryalarm\":0,\"tamper\":0,\"wgformat\":0,\"wgoutput\":0,\"cardoutput\":0,\"dayzone\":[{\"day\":[{\"section\":\"01:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"02:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"03:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"04:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"05:00~00:0\n"
				+ "0\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"06:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"07:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"08:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]}],\"weekzone\":[{\"week\":[{\"day\":0},{\"day\":1},{\"day\":2},{\"day\":3},{\"day\":4},{\"day\":5},{\"day\":6}]},{\"week\":[{\"day\":10},{\"day\":11},{\"day\":12},{\"day\":13},{\"day\":14},{\"day\":15},{\"day\":16}]},{\"week\":[{\"day\":20},{\"day\":21},{\"day\":22},{\"day\":23},{\"day\":24},{\"day\":25},{\"day\":26}]},\n"
				+ "{\"week\":[{\"day\":30},{\"day\":31},{\"day\":32},{\"day\":33},{\"day\":34},{\"day\":35},{\"day\":36}]},{\"week\":[{\"day\":40},{\"day\":41},{\"day\":42},{\"day\":43},{\"day\":44},{\"day\":45},{\"day\":46}]},{\"week\":[{\"day\":50},{\"day\":51},{\"day\":52},{\"day\":53},{\"day\":54},{\"day\":55},{\"day\":56}]},{\"week\":[{\"day\":60},{\"day\":61},{\"day\":62},{\"day\":63},{\"day\":64},{\"day\":65},{\"day\":66}]},{\"week\":[{\"day\":70},{\"day\":71},{\"day\":72},{\"day\":73},{\"day\":74},{\"day\":75},{\"day\":76}]}],\"lockgroup\":[{\"group\":0},{\"group\":1},{\"group\":2},{\"group\":3},{\"group\":4}],\"nopentime\":[{\"day\":0},{\"day\":0},{\"day\":0},{\"day\":0},{\"day\":0},{\"day\":0},{\"day\":0}]}\n"
				+ "";

		String messageTemp = "{\"cmd\":\"setuserlock\",\"count\":1,\"record\":[{\"enrollid\":1,\"weekzone\":1,\"weekzone2\":3,\"group\":1,\"starttime\":\"2010-11-11 00:00:00\",\"endtime\":\"2030-11-11 00:00:00\"}]}";
		String s5 = "{\"cmd\":\"enableuser\",\"enrollid\":1,\"enflag\":0}";
		String s6 = "{\"cmd\":\"getusername\",\"enrollid\":1}";
		String message22 = "{\"cmd\":\"initsys\"}";

		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message22);
		machineCommand.setName("initsys");
		machineCommand.setStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSendStatus(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);

		return Msg.success();
	}

	/* é‡‡é›†æ‰€æœ‰çš„è€ƒå‹¤è®°å½•ï¼Œé¢å‘æ‰€æœ‰æœºå™¨ */
	@ResponseBody
	@RequestMapping(value = "/getAllLog", method = RequestMethod.GET)
	public Msg getAllLog(@RequestParam("deviceSn") String deviceSn) {
		String message = "{\"cmd\":\"getalllog\",\"stn\":true}";
		// String
		// messageTemp="{\"cmd\":\"getalllog\",\"stn\":true,\"from\":\"2020-12-03\",\"to\":\"2020-12-30\"}";
		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("getalllog");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);
		return Msg.success();

	}

	/* é‡‡é›†æ‰€æœ‰çš„è€ƒå‹¤è®°å½•ï¼Œé¢å‘æ‰€æœ‰æœºå™¨ */
	@ResponseBody
	@RequestMapping(value = "/getNewLog", method = RequestMethod.GET)
	public Msg getNewLog(@RequestParam("deviceSn") String deviceSn) {
		String message = "{\"cmd\":\"getnewlog\",\"stn\":true}";
		// String
		// messageTemp="{\"cmd\":\"getalllog\",\"stn\":true,\"from\":\"2020-12-03\",\"to\":\"2020-12-30\"}";
		System.out.println(message);
		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("getnewlog");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);
		return Msg.success();

	}

	/* æ˜¾ç¤ºå‘˜å·¥åˆ—è¡¨ */
	@RequestMapping(value = "/emps")
	@ResponseBody
	public Msg getAllPersonFromDB(@RequestParam(value = "pn", defaultValue = "1") Integer pn) {
		PageHelper.startPage(pn, 8);
		List<Person> personList = personService.selectAll();
		PageInfo<Person> personPage = new PageInfo<Person>(personList, 5);
		List<UserInfo> emps = new ArrayList<UserInfo>(personList.size());
		for (int i = 0; i < personList.size(); i++) {
			UserInfo userInfo = new UserInfo();
			userInfo.setEnrollId(personList.get(i).getId());
			userInfo.setAdmin(personList.get(i).getRollId());
			userInfo.setName(personList.get(i).getName());
			emps.add(userInfo);
		}
		Page<UserInfo> pagedUsers = new Page<UserInfo>(personPage.getPageNum(), personPage.getPageSize());
		pagedUsers.setTotal(personPage.getTotal());
		pagedUsers.addAll(emps);
		PageInfo<UserInfo> page = new PageInfo<UserInfo>(pagedUsers, 5);
		return Msg.success().add("pageInfo", page);

	}

	/* æ˜¾ç¤ºæ‰€æœ‰çš„æ‰“å¡è®°å½• */
	@RequestMapping(value = "/records")
	@ResponseBody
	public Msg getAllLogFromDB(@RequestParam(value = "pn", defaultValue = "1") Integer pn) {
		PageHelper.startPage(pn, 8);

		List<Records> records = recordService.selectAllRecords();

		PageInfo page = new PageInfo(records, 5);

		return Msg.success().add("pageInfo", page);

	}


	@RequestMapping(value = "/openDoor", method = RequestMethod.GET)
	@ResponseBody
	public Msg openDoor(@RequestParam("doorNum") int doorNum, @RequestParam("deviceSn") String deviceSn) {
		String message = "{\"cmd\":\"opendoor\"" + ",\"doornum\":" + doorNum + "}";
		String normalizedSn = deviceSn == null ? "" : deviceSn.trim();
		try {
			if (WebSocketPool.getDeviceSocketBySn(normalizedSn) != null) {
				WebSocketPool.sendMessageToDeviceStatus(normalizedSn, message);
				return Msg.success().add("sentDirect", true).add("queued", false).add("deviceSn", normalizedSn);
			}
			if (WebSocketPool.wsDevice.size() == 1) {
				String fallbackSn = WebSocketPool.wsDevice.keySet().iterator().next();
				WebSocketPool.sendMessageToDeviceStatus(fallbackSn, message);
				return Msg.success().add("sentDirect", true).add("queued", false).add("deviceSn", fallbackSn)
						.add("fallbackSingleDevice", true);
			}
		} catch (Exception ex) {
			// Return explicit failure below.
		}
		return Msg.fail().add("error", "Device is not online on websocket, opendoor was not sent.")
				.add("sentDirect", false).add("queued", false).add("deviceSn", normalizedSn);
	}

	@RequestMapping(value = "/getDevLock", method = RequestMethod.GET)
	@ResponseBody
	public Msg getDevLock(@RequestParam("deviceSn") String deviceSn) {
		String message = "{\"cmd\":\"getdevlock\"}";

		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("getdevlock");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);
		return Msg.success();

	}

	@RequestMapping(value = "/geUSerLock", method = RequestMethod.GET)
	@ResponseBody
	public Msg getUserLock(@RequestParam("enrollId") Integer enrollId, @RequestParam("deviceSn") String deviceSn) {

		String message = "{\"cmd\":\"getuserlock\",\"enrollid\":" + enrollId + "}";
		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("getuserlock");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);

		return Msg.success();
	}

	@RequestMapping(value = "/cleanAdmin", method = RequestMethod.GET)
	@ResponseBody
	public Msg cleanAdmin(@RequestParam("deviceSn") String deviceSn) {
		String message = "{\"cmd\":\"cleanadmin\"}";

		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("cleanadmin");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);
		return Msg.success();

	}

	@RequestMapping(value = "/synchronizeTime", method = RequestMethod.GET)
	@ResponseBody
	public Msg synchronizeTime(@RequestParam("deviceSn") String deviceSn) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String timeStr = sdf.format(new Date());
		String message = "{\"cmd\":\"settime\",\"cloudtime\":\"" + timeStr + "\"}";
		if (WebSocketPool.wsDevice.get(deviceSn) != null) {
			WebSocketPool.sendMessageToDeviceStatus(deviceSn, message);
			System.out.println("Send command to machine:" + message);
			return Msg.success();
		}
		return Msg.fail();

	}

	private boolean isSupportedSetBackupNum(int backupNum) {
		return backupNum == -1 || isSupportedEnrollBackupNum(backupNum);
	}

	private boolean isFaceBackupNum(int backupNum) {
		return backupNum == 50 || (backupNum >= 20 && backupNum <= 27);
	}

	private boolean isSupportedEnrollBackupNum(int backupNum) {
		return backupNum == 10 || backupNum == 11 || backupNum == 50 || (backupNum >= 20 && backupNum <= 27);
	}

}


