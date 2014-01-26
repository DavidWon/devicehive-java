package com.devicehive.client.impl;


import com.devicehive.client.DeviceController;
import com.devicehive.client.impl.context.HiveContext;
import com.devicehive.client.model.Device;
import com.devicehive.client.model.DeviceClass;
import com.devicehive.client.model.DeviceEquipment;
import com.devicehive.client.model.exceptions.HiveClientException;
import com.google.common.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.HttpMethod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.devicehive.client.impl.json.strategies.JsonPolicyDef.Policy.*;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

class DeviceControllerImpl implements DeviceController {

    private static final Logger logger = LoggerFactory.getLogger(DeviceControllerImpl.class);
    private final HiveContext hiveContext;

    public DeviceControllerImpl(HiveContext hiveContext) {
        this.hiveContext = hiveContext;
    }

    //for devices
    @Override
    public List<Device> listDevices(String name, String namePattern, String status, Integer networkId,
                                    String networkName, Integer deviceClassId, String deviceClassName,
                                    String deviceClassVersion, String sortField, String sortOrder, Integer take,
                                    Integer skip) {
        logger.debug("Device: list requested with following parameters: name {}, name pattern {}, network id {}, " +
                "network name {}, device class id {}, device class name {}, device class version {}, sort field {}, " +
                "sort order {}, take {}, skip {}", name, namePattern, networkId, networkName, deviceClassId,
                deviceClassName, deviceClassVersion, sortField, sortOrder, take, skip);
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("name", name);
        queryParams.put("namePattern", namePattern);
        queryParams.put("status", status);
        queryParams.put("networkId", networkId);
        queryParams.put("networkName", networkName);
        queryParams.put("deviceClassId", deviceClassId);
        queryParams.put("deviceClassName", deviceClassName);
        queryParams.put("deviceClassVersion", deviceClassVersion);
        queryParams.put("sortField", sortField);
        queryParams.put("sortOrder", sortOrder);
        queryParams.put("take", take);
        queryParams.put("skip", skip);
        String path = "/device";
        List<Device> result = hiveContext.getHiveRestClient().execute(path, HttpMethod.GET, null, queryParams,
                new TypeToken<List<Device>>() {
                }.getType(), DEVICE_PUBLISHED);
        logger.debug("Device: list request proceed successfully for following parameters: name {}, name pattern {}, " +
                "network id {}, network name {}, device class id {}, device class name {}, device class version {}, " +
                "sort field {}, sort order {}, take {}, skip {}", name, namePattern, networkId, networkName,
                deviceClassId, deviceClassName, deviceClassVersion, sortField, sortOrder, take, skip);
        return result;
    }

    @Override
    public Device getDevice(String deviceId) {
        logger.debug("Device: get requested for device id {}", deviceId);
        String path = "/device/" + deviceId;
        Device result = hiveContext.getHiveRestClient().execute(path, HttpMethod.GET, null, Device.class,
                DEVICE_PUBLISHED);
        logger.debug("Device: get request proceed successfully for device id {}. Device name {}, status {}, data {}, " +
                "network id {}, network name {}, device class id {}, device class name {}, device class verison {}",
                deviceId, result.getName(), result.getStatus(), result.getData(),
                result.getNetwork() != null ? result.getNetwork().getId() : null,
                result.getNetwork() != null ? result.getNetwork().getName() : null,
                result.getDeviceClass().getId(), result.getDeviceClass().getName(),
                result.getDeviceClass().getVersion());
        return result;
    }

    @Override
    public void registerDevice(String deviceId, Device device) throws HiveClientException {
        if (device == null) {
            throw new HiveClientException("Device cannot be null!", BAD_REQUEST.getStatusCode());
        }
        logger.debug("Device: register requested for device id {} Device name {}, status {}, data {}, " +
                "network id {}, network name {}, device class id {}, device class name {}, device class verison {}",
                deviceId, device.getName(), device.getStatus(), device.getData(),
                device.getNetwork() != null ? device.getNetwork().getId() : null,
                device.getNetwork() != null ? device.getNetwork().getName() : null,
                device.getDeviceClass() != null ? device.getDeviceClass().getId() : null,
                device.getDeviceClass() != null ? device.getDeviceClass().getName() : null,
                device.getDeviceClass() != null ? device.getDeviceClass().getVersion() : null);
        String path = "/device/" + deviceId;
        hiveContext.getHiveRestClient().execute(path, HttpMethod.PUT, null, device, DEVICE_PUBLISHED);
        logger.debug("Device: register request proceed successfully for device id {} Device name {}, status {}, " +
                "data {}, network id {}, network name {}, device class id {}, device class name {}, device class verison {}",
                deviceId, device.getName(), device.getStatus(), device.getData(),
                device.getNetwork() != null ? device.getNetwork().getId() : null,
                device.getNetwork() != null ? device.getNetwork().getName() : null,
                device.getDeviceClass() != null ? device.getDeviceClass().getId() : null,
                device.getDeviceClass() != null ? device.getDeviceClass().getName() : null,
                device.getDeviceClass() != null ? device.getDeviceClass().getVersion() : null);
    }

    @Override
    public void deleteDevice(String deviceId) {
        logger.debug("Device: delete requested for device with id {}", deviceId);
        String path = "/device/" + deviceId;
        hiveContext.getHiveRestClient().execute(path, HttpMethod.DELETE);
        logger.debug("Device: delete request proceed successfully for device with id {}", deviceId);
    }

    @Override
    public List<DeviceEquipment> getDeviceEquipment(String deviceId) {
        logger.debug("Device: equipment requested for device with id {}", deviceId);
        String path = "/device/" + deviceId + "/equipment";
        List<DeviceEquipment> result = hiveContext.getHiveRestClient()
                .execute(path, HttpMethod.GET, null, null, null, new TypeToken<List<DeviceEquipment>>() {
                }.getType(), null, DEVICE_EQUIPMENT_SUBMITTED);
        logger.debug("Device: equipment request proceed successfully for device with id {}", deviceId);
        return result;
    }

    //for device classes
    @Override
    public List<DeviceClass> listDeviceClass(String name, String namePattern, String version, String sortField,
                                             String sortOrder, Integer take, Integer skip) {
        logger.debug("DeviceClass: list requested with parameters: name {}, name pattern {}, version {}, " +
                "sort field {}, sort order {}, take param {}, skip  param {}", name, namePattern, version, sortField,
                sortOrder, take, skip);
        String path = "/device/class";
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("name", name);
        queryParams.put("namePattern", namePattern);
        queryParams.put("version", version);
        queryParams.put("sortField", sortField);
        queryParams.put("sortOrder", sortOrder);
        queryParams.put("take", take);
        queryParams.put("skip", skip);
        List<DeviceClass> result = hiveContext.getHiveRestClient().execute(path, HttpMethod.GET, null, queryParams,
                new TypeToken<List<DeviceClass>>() {
                }.getType(), DEVICECLASS_LISTED);
        logger.debug("DeviceClass: list request proceed for following params: name {}, name pattern {}, version {}, " +
                "sort field {}, sort order {}, take param {}, skip  param {}", name, namePattern, version, sortField,
                sortOrder, take, skip);
        return result;
    }

    @Override
    public DeviceClass getDeviceClass(long classId) {
        logger.debug("DeviceClass: get requested for class with id {}", classId);
        String path = "/device/class/" + classId;
        DeviceClass result = hiveContext.getHiveRestClient()
                .execute(path, HttpMethod.GET, null, DeviceClass.class, DEVICECLASS_PUBLISHED);
        logger.debug("DeviceClass: get request proceed for class with id {}", classId);
        return result;
    }

    @Override
    public long insertDeviceClass(DeviceClass deviceClass) throws HiveClientException {
        if (deviceClass == null) {
            throw new HiveClientException("Device class cannot be null!", BAD_REQUEST.getStatusCode());
        }
        logger.debug("DeviceClass: insert requested for device class with name {}, version {}",
                deviceClass.getName(), deviceClass.getVersion());
        String path = "/device/class";
        DeviceClass inserted = hiveContext.getHiveRestClient().execute(path, HttpMethod.POST, null, null, deviceClass,
                DeviceClass.class, DEVICECLASS_PUBLISHED, DEVICECLASS_SUBMITTED);
        logger.debug("DeviceClass: insert request proceed for device class with name {}, version {}. Result id {}",
                deviceClass.getName(), deviceClass.getVersion(), inserted.getId());
        return inserted.getId();
    }

    @Override
    public void updateDeviceClass(long classId, DeviceClass deviceClass) throws HiveClientException {
        if (deviceClass == null) {
            throw new HiveClientException("Device class cannot be null!", BAD_REQUEST.getStatusCode());
        }
        logger.debug("DeviceClass: update requested for device class with id {}, name {}, version {}",
                classId, deviceClass.getName(), deviceClass.getVersion());
        String path = "/device/class/" + classId;
        hiveContext.getHiveRestClient().execute(path, HttpMethod.PUT, null, deviceClass, DEVICECLASS_PUBLISHED);
        logger.debug("DeviceClass: update request proceed for device class with id {}, name {}, version {}",
                classId, deviceClass.getName(), deviceClass.getVersion());
    }

    @Override
    public void deleteDeviceClass(long classId) {
        logger.debug("DeviceClass: delete requested for class with id {}", classId);
        String path = "/device/class" + classId;
        hiveContext.getHiveRestClient().execute(path, HttpMethod.DELETE);
        logger.debug("DeviceClass: delete request proceed for class with id {}", classId);
    }
}