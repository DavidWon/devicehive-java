package com.devicehive.controller;


import com.devicehive.auth.HiveRoles;
import com.devicehive.configuration.Messages;
import com.devicehive.controller.util.ResponseFactory;
import com.devicehive.json.strategies.JsonPolicyApply;
import com.devicehive.json.strategies.JsonPolicyDef;
import com.devicehive.model.Equipment;
import com.devicehive.model.ErrorResponse;
import com.devicehive.model.updates.EquipmentUpdate;
import com.devicehive.service.DeviceClassService;
import com.devicehive.service.EquipmentService;
import com.devicehive.util.LogExecutionTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.ejb.EJB;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.devicehive.configuration.Constants.DEVICE_CLASS_ID;
import static com.devicehive.configuration.Constants.ID;
import static com.devicehive.json.strategies.JsonPolicyDef.Policy.EQUIPMENTCLASS_SUBMITTED;
import static com.devicehive.json.strategies.JsonPolicyDef.Policy.EQUIPMENT_PUBLISHED;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.METHOD_NOT_ALLOWED;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;

@Path("/device/class/{deviceClassId}/equipment")
@RolesAllowed(HiveRoles.ADMIN)
@LogExecutionTime
public class EquipmentController {

    private static final Logger logger = LoggerFactory.getLogger(EquipmentController.class);

    @EJB
    private DeviceClassService deviceClassService;

    @EJB
    private EquipmentService equipmentService;


    /**
     * Gets current state of device equipment. <code> [ { "id":1, "timestamp": "1970-01-01 00:00:00.0", "parameters":{/
     * *custom json object* /} }, { "id":2, "timestamp": "1970-01-01 00:00:00.0", "parameters":{/ *custom json object*
     * /} } ] <p/> </code>
     *
     * @param classId device class id
     * @param eqId    equipment id
     */
    @GET
    @Path("/{id}")
    public Response getEquipment(@PathParam(DEVICE_CLASS_ID) long classId, @PathParam(ID) long eqId) {

        logger.debug("Device class's equipment get requested");
        Equipment result = equipmentService.getByDeviceClass(classId, eqId);

        if (result == null) {
            logger.debug("No equipment with id = {} for device class with id = {} found", eqId, classId);
            return ResponseFactory.response(NOT_FOUND,
                                            new ErrorResponse(NOT_FOUND.getStatusCode(),
                                                              String.format(Messages.EQUIPMENT_NOT_FOUND, eqId,
                                                                            classId)));
        }
        logger.debug("Device class's equipment get proceed successfully");

        return ResponseFactory.response(OK, result, EQUIPMENT_PUBLISHED);
    }

    /**
     * Adds new equipment type to device class
     *
     * @param classId device class id
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response insertEquipment(@PathParam(DEVICE_CLASS_ID) long classId, Equipment equipment) {

        logger.debug("Insert device class's equipment requested");
        Equipment result = deviceClassService.createEquipment(classId, equipment);
        logger.debug("New device class's equipment created");

        return ResponseFactory.response(CREATED, result, EQUIPMENTCLASS_SUBMITTED);
    }

    /**
     * Updates device class' equipment. None of following parameters are mandatory. Parameters, if left unspecified,
     * remains unchanged, instead setting parameter to null will null corresponding value. In following JSON <p/> name
     * 	Equipment display name. code 	Equipment code. It's used to reference particular equipment and it should be
     * unique within a device class. type 	Equipment type. An arbitrary string representing equipment capabilities. data
     * 	Equipment data, a JSON object with an arbitrary structure. <p/> <code> { "name": "equipment name", "code":
     * "equipment_code", "type": "equipment_type", "data": {/ * json object* /} } </code>
     *
     * @param classId         id of class
     * @param eqId            equipment id
     * @param equipmentUpdate Json  object
     * @return empty response with status 201 in case of success, empty response with status 404, if there's no such
     *         record
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateEquipment(
        @PathParam(DEVICE_CLASS_ID) long classId,
        @PathParam(ID) long eqId,
        @JsonPolicyApply(JsonPolicyDef.Policy.EQUIPMENT_PUBLISHED) EquipmentUpdate equipmentUpdate) {

        logger.debug("Update device class's equipment requested");

        if (!equipmentService.update(equipmentUpdate, eqId, classId)) {
            logger.debug("Unable to update equipment. Equipment with id = {} for device class with id = {} not found",
                         eqId, classId);
            return ResponseFactory.response(NOT_FOUND,
                                            new ErrorResponse(NOT_FOUND.getStatusCode(),
                                                              String.format(Messages.EQUIPMENT_NOT_FOUND, eqId,
                                                                            classId)));
        }

        logger.debug("Update device class's equipment finished successfully");

        return ResponseFactory.response(NO_CONTENT);
    }

    /**
     * Will cascade deletes specified equipment and all data for this equipment for all devise of this type.
     *
     * @param classId Device class id
     * @param eqId    Equipment id
     * @return empty body, 204 if success, 404 if no record found
     */
    @DELETE
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteEquipment(@PathParam(DEVICE_CLASS_ID) long classId, @PathParam(ID) long eqId) {

        logger.debug("Delete device class's equipment requested");
        equipmentService.delete(eqId, classId);
        logger.debug("Delete device class's equipment finished");

        return ResponseFactory.response(NO_CONTENT);
    }

    /**
     * Gets current state of device equipment. <code> [ { "id":1, "timestamp": "1970-01-01 00:00:00.0", "parameters":{/
     * *custom json object* /} }, { "id":2, "timestamp": "1970-01-01 00:00:00.0", "parameters":{/ *custom json object*
     * /} } ] <p/> </code>
     */
    @GET
    public Response getEquipment() {
        return ResponseFactory.response(METHOD_NOT_ALLOWED, new ErrorResponse(METHOD_NOT_ALLOWED.getStatusCode(),
                                                                              METHOD_NOT_ALLOWED.getReasonPhrase()));
    }

}
