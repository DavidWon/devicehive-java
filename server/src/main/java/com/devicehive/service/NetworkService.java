package com.devicehive.service;

import com.devicehive.auth.AllowedKeyAction;
import com.devicehive.auth.CheckPermissionsHelper;
import com.devicehive.auth.HivePrincipal;
import com.devicehive.auth.HiveSecurityContext;
import com.devicehive.configuration.ConfigurationService;
import com.devicehive.configuration.Messages;
import com.devicehive.dao.AccessKeyDAO;
import com.devicehive.dao.NetworkDAO;
import com.devicehive.exceptions.HiveException;
import com.devicehive.model.AccessKey;
import com.devicehive.model.AccessKeyPermission;
import com.devicehive.model.Device;
import com.devicehive.model.Network;
import com.devicehive.model.NullableWrapper;
import com.devicehive.model.User;
import com.devicehive.model.updates.NetworkUpdate;
import com.devicehive.util.HiveValidator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.validation.constraints.NotNull;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

@Stateless
public class NetworkService {

    public static final String ALLOW_NETWORK_AUTO_CREATE = "allowNetworkAutoCreate";

    @EJB
    private NetworkDAO networkDAO;
    @EJB
    private UserService userService;
    @EJB
    private AccessKeyService accessKeyService;
    @EJB
    private AccessKeyDAO accessKeyDAO;
    @EJB
    private DeviceService deviceService;
    @EJB
    private ConfigurationService configurationService;
    @EJB
    private HiveValidator hiveValidator;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Network getWithDevicesAndDeviceClasses(@NotNull Long networkId,
                                                  @NotNull HiveSecurityContext hiveSecurityContext) {
        HivePrincipal principal = hiveSecurityContext.getHivePrincipal();
        if (principal.getUser() != null) {
            List<Network> found = networkDAO.getNetworkList(principal.getUser(), null, Arrays.asList(networkId));
            if (found.isEmpty()) {
                return null;
            }
            List<Device> devices = deviceService.getList(networkId, principal);
            Network result = found.get(0);
            result.setDevices(new HashSet<>(devices));
            return result;
        } else {
            AccessKey key = principal.getKey();
            User user = userService.findUserWithNetworks(key.getUser().getId());
            List<Network> found = networkDAO.getNetworkList(user,
                                                            key.getPermissions(),
                                                            Arrays.asList(networkId));
            Network result = found.isEmpty() ? null : found.get(0);
            if (result == null) {
                return result;
            }
            //to get proper devices 1) get access key with all permissions 2) get devices for required network
            Set<AccessKeyPermission> filtered = CheckPermissionsHelper
                .filterPermissions(key.getPermissions(), AllowedKeyAction.Action.GET_DEVICE,
                                   hiveSecurityContext.getClientInetAddress(), hiveSecurityContext.getOrigin());
            if (filtered.isEmpty()) {
                result.setDevices(null);
                return result;
            }
            Set<Device> devices =
                new HashSet<>(deviceService.getList(result.getId(), principal));
            result.setDevices(devices);
            return result;
        }
    }

    public boolean delete(long id) {
        return networkDAO.delete(id);
    }

    public Network create(Network newNetwork) {
        if (newNetwork.getId() != null) {
            throw new HiveException(Messages.ID_NOT_ALLOWED, BAD_REQUEST.getStatusCode());
        }
        Network existing = networkDAO.findByName(newNetwork.getName());
        if (existing != null) {
            throw new HiveException(Messages.DUPLICATE_NETWORK, FORBIDDEN.getStatusCode());
        }
        return networkDAO.createNetwork(newNetwork);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Network update(@NotNull Long networkId, NetworkUpdate networkUpdate) {
        Network existing = getById(networkId);
        if (existing == null) {
            throw new HiveException(String.format(Messages.NETWORK_NOT_FOUND, networkId), NOT_FOUND.getStatusCode());
        }
        if (networkUpdate.getKey() != null) {
            existing.setKey(networkUpdate.getKey().getValue());
        }
        if (networkUpdate.getName() != null) {
            existing.setName(networkUpdate.getName().getValue());
        }
        if (networkUpdate.getDescription() != null) {
            existing.setDescription(networkUpdate.getDescription().getValue());
        }
        hiveValidator.validate(existing);
        return networkDAO.updateNetwork(existing);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Network> list(String name,
                              String namePattern,
                              String sortField,
                              boolean sortOrder,
                              Integer take,
                              Integer skip,
                              HivePrincipal principal) {
        return networkDAO.list(name, namePattern, sortField, sortOrder, take, skip, principal);
    }

    @TransactionAttribute
    public Network createOrVeriryNetwork(NullableWrapper<Network> network) {
        Network stored;
        //case network is not defined
        if (network == null || network.getValue() == null) {
            return null;
        }
        Network update = network.getValue();

        if (update.getId() != null) {
            stored = networkDAO.getById(update.getId());
        } else {
            stored = networkDAO.findByName(update.getName());
        }

        if (stored != null) {
            if (stored.getKey() != null) {
                if (!stored.getKey().equals(update.getKey())) {
                    throw new HiveException(Messages.INVALID_NETWORK_KEY, FORBIDDEN.getStatusCode());
                }
            }
        } else {
            if (update.getId() != null) {
                throw new HiveException(Messages.INVALID_REQUEST_PARAMETERS, BAD_REQUEST.getStatusCode());
            }
            if (configurationService.getBoolean(ALLOW_NETWORK_AUTO_CREATE, false)) {
                stored = networkDAO.createNetwork(update);
            }
        }
        assert (stored != null);
        return stored;
    }

    @TransactionAttribute
    public Network createOrUpdateNetworkByUser(NullableWrapper<Network> network, User user) {
        Network stored;

        //case network is not defined
        if (network == null || network.getValue() == null) {
            return null;
        }

        Network update = network.getValue();

        if (update.getId() != null) {
            stored = networkDAO.getWithDevicesAndDeviceClasses(update.getId());
        } else {
            stored = networkDAO.findByName(update.getName());
        }

        if (stored != null) {
            if (stored.getKey() != null) {
                if (!stored.getKey().equals(update.getKey())) {
                    throw new HiveException(Messages.INVALID_NETWORK_KEY, FORBIDDEN.getStatusCode());
                }
            }
            if (!userService.hasAccessToNetwork(user, stored)) {
                throw new HiveException(Messages.NO_ACCESS_TO_NETWORK, FORBIDDEN.getStatusCode());
            }
        } else {
            if (update.getId() != null) {
                throw new HiveException(Messages.NETWORK_NOT_FOUND, BAD_REQUEST.getStatusCode());
            }
            if (user.isAdmin() || configurationService.getBoolean(ALLOW_NETWORK_AUTO_CREATE, false)) {
                stored = networkDAO.createNetwork(update);
            } else {
                throw new HiveException(Messages.NETWORK_CREATION_NOT_ALLOWED, FORBIDDEN.getStatusCode());
            }
        }
        return stored;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Network createOrVeriryNetworkByKey(NullableWrapper<Network> network, AccessKey key) {
        Network stored;

        //case network is not defined
        if (network == null || network.getValue() == null) {
            return null;
        }

        Network update = network.getValue();

        if (update.getId() != null) {
            stored = networkDAO.getById(update.getId());
        } else {
            stored = networkDAO.findByName(update.getName());
        }
        if (stored != null) {
            if (stored.getKey() != null) {
                if (!stored.getKey().equals(update.getKey())) {
                    throw new HiveException(Messages.INVALID_NETWORK_KEY, FORBIDDEN.getStatusCode());
                }
                if (!accessKeyService.hasAccessToNetwork(key, stored)) {
                    throw new HiveException(Messages.NO_ACCESS_TO_NETWORK, FORBIDDEN.getStatusCode());
                }
            }
        } else {
            if (configurationService.getBoolean(ALLOW_NETWORK_AUTO_CREATE, false)) {
                stored = networkDAO.createNetwork(update);
            } else {
                throw new HiveException(Messages.NETWORK_CREATION_NOT_ALLOWED, FORBIDDEN.getStatusCode());
            }
        }
        return stored;
    }

    private Network getById(long id) {
        return networkDAO.getById(id);
    }
}
