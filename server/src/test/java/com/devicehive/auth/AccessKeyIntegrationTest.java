package com.devicehive.auth;

import com.devicehive.Constants;
import com.devicehive.controller.DeviceCommandController;
import com.devicehive.controller.DeviceController;
import com.devicehive.controller.DeviceNotificationController;
import com.devicehive.controller.NetworkController;
import com.devicehive.dao.AccessKeyDAO;
import com.devicehive.dao.AccessKeyPermissionDAO;
import com.devicehive.dao.DeviceDAO;
import com.devicehive.dao.UserDAO;
import com.devicehive.exceptions.HiveException;
import com.devicehive.model.AccessKey;
import com.devicehive.model.AccessKeyPermission;
import com.devicehive.model.JsonStringWrapper;
import com.devicehive.model.User;
import com.devicehive.model.UserRole;
import com.devicehive.model.UserStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.interceptor.InvocationContext;
import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class AccessKeyIntegrationTest {

    private static final User ADMIN = new User() {
        {
            setId(Constants.ACTIVE_ADMIN_ID);
            setLogin("admin");
            setRole(UserRole.ADMIN);
            setStatus(UserStatus.ACTIVE);
        }

        private static final long serialVersionUID = -8141654148541503342L;
    };
    private static final User CLIENT = new User() {
        {
            setId(Constants.ACTIVE_CLIENT_ID);
            setLogin("client");
            setRole(UserRole.CLIENT);
            setStatus(UserStatus.ACTIVE);
        }

        private static final long serialVersionUID = -765281406898288088L;
    };
    List<Method> allAvailableMethods;
    @Mock
    private AccessKeyDAO accessKeyDAO;
    @Mock
    private DeviceDAO deviceDAO;
    @Mock
    private AccessKeyPermissionDAO permissionDAO;
    @Mock
    private UserDAO userDAO;
    private AccessKeyInterceptor interceptor = new AccessKeyInterceptor();
    @Mock
    private InvocationContext context;
    private int methodCalls;

    @Before
    public void initializeDependencies() {
        MockitoAnnotations.initMocks(this);

        Method[] deviceControllerMethods = DeviceController.class.getMethods();
        Method[] notificationControllerMethods = DeviceNotificationController.class.getMethods();
        Method[] commandControllerMethods = DeviceCommandController.class.getMethods();
        Method[] networkControllerMethods = NetworkController.class.getMethods();
        allAvailableMethods = new ArrayList<>();
        allAvailableMethods.addAll(Arrays.asList(deviceControllerMethods));
        allAvailableMethods.addAll(Arrays.asList(notificationControllerMethods));
        allAvailableMethods.addAll(Arrays.asList(commandControllerMethods));
        allAvailableMethods.addAll(Arrays.asList(networkControllerMethods));
        Iterator<Method> iterator = allAvailableMethods.iterator();
        while (iterator.hasNext()) {
            Method currentMethod = iterator.next();
            if (!currentMethod.isAnnotationPresent(AllowedKeyAction.class)) {
                iterator.remove();
            } else {
                methodCalls++;
            }
        }
    }

    @Test
    public void actionsCaseAllowed() {
        /**
         * Only actions field is not null
         */
        AccessKey accessKey = new AccessKey();
        accessKey.setUser(CLIENT);

        for (Method method : allAvailableMethods) {
            when(context.getMethod()).thenReturn(method);
            try {
                AllowedKeyAction allowedActionAnnotation = method.getAnnotation(AllowedKeyAction.class);
                List<AllowedKeyAction.Action> allowedActions = Arrays.asList(allowedActionAnnotation.action());
                HiveSecurityContext hiveSecurityContext = new HiveSecurityContext();
                hiveSecurityContext.setHivePrincipal(new HivePrincipal(null, null, accessKey));
                hiveSecurityContext.setOrigin("http://test.devicehive.com");
                try {
                    hiveSecurityContext.setClientInetAddress(InetAddress.getByName("8.8.8.8"));
                } catch (UnknownHostException e) {
                    fail("Unexpected exception");
                }
                interceptor.setHiveSecurityContext(hiveSecurityContext);
                if (allowedActions.contains(AllowedKeyAction.Action.GET_DEVICE)) {
                    actionTestProcess(accessKey, "[GetDevice,GetNetwork,GetDeviceNotification]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.REGISTER_DEVICE)) {
                    actionTestProcess(accessKey, "[CreateDeviceNotification,RegisterDevice]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.CREATE_DEVICE_COMMAND)) {
                    actionTestProcess(accessKey, "[CreateDeviceCommand,UpdateDeviceCommand]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.CREATE_DEVICE_NOTIFICATION)) {
                    actionTestProcess(accessKey,
                                      "[CreateDeviceNotification,CreateDeviceCommand,UpdateDeviceCommand]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.GET_DEVICE_COMMAND)) {
                    actionTestProcess(accessKey, "[CreateDeviceNotification,GetDeviceCommand,UpdateDeviceCommand]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.GET_DEVICE_NOTIFICATION)) {
                    actionTestProcess(accessKey,
                                      "[CreateDeviceNotification,GetDeviceNotification,UpdateDeviceCommand]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.UPDATE_DEVICE_COMMAND)) {
                    actionTestProcess(accessKey,
                                      "[CreateDeviceNotification,GetDeviceNotification,UpdateDeviceCommand]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.GET_NETWORK)) {
                    actionTestProcess(accessKey,
                                      "[CreateDeviceNotification,GetNetwork,UpdateDeviceCommand]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.GET_DEVICE_STATE)) {
                    actionTestProcess(accessKey, "[GetDeviceState]");
                }
            } catch (Exception e) {
                fail("No exceptions expected from interceptor");
            } finally {
                accessKey.getPermissions().clear();
            }
        }

    }

    @Test
    public void actionsCaseNotAllowed() throws Exception {
        /**
         * Only actions field is not null
         */
        AccessKey accessKey = new AccessKey();
        accessKey.setUser(ADMIN);
        int exceptionsCounter = 0;
        for (Method method : allAvailableMethods) {
            when(context.getMethod()).thenReturn(method);
            AllowedKeyAction allowedActionAnnotation = method.getAnnotation(AllowedKeyAction.class);
            List<AllowedKeyAction.Action> allowedActions = Arrays.asList(allowedActionAnnotation.action());
            HiveSecurityContext hiveSecurityContext = new HiveSecurityContext();
            hiveSecurityContext.setHivePrincipal(new HivePrincipal(null, null, accessKey));
            hiveSecurityContext.setOrigin("http://test.devicehive.com");
            try {
                hiveSecurityContext.setClientInetAddress(InetAddress.getByName("8.8.8.8"));
            } catch (UnknownHostException e) {
                fail("Unexpected exception");
            }
            interceptor.setHiveSecurityContext(hiveSecurityContext);
            try {
                //if some controllers will contain more than 1 action per method, should be changed
                if (allowedActions.contains(AllowedKeyAction.Action.GET_DEVICE)) {
                    actionTestProcess(accessKey, "[RegisterDevice]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.REGISTER_DEVICE)) {
                    actionTestProcess(accessKey, "[GetDevice]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.GET_DEVICE_STATE)) {
                    actionTestProcess(accessKey, "[GetDevice]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.CREATE_DEVICE_COMMAND)) {
                    actionTestProcess(accessKey, "[CreateDeviceNotification]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.CREATE_DEVICE_NOTIFICATION)) {
                    actionTestProcess(accessKey, "[GetDeviceCommand]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.GET_DEVICE_COMMAND)) {
                    actionTestProcess(accessKey, "[CreateDeviceNotification]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.GET_DEVICE_NOTIFICATION)) {
                    actionTestProcess(accessKey, "[CreateDeviceNotification]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.UPDATE_DEVICE_COMMAND)) {
                    actionTestProcess(accessKey, "[GetNetwork]");
                }
                if (allowedActions.contains(AllowedKeyAction.Action.GET_NETWORK)) {
                    actionTestProcess(accessKey,
                                      "[CreateDeviceNotification]");
                }
            } catch (HiveException e) {
                if (e.getCode() != Response.Status.UNAUTHORIZED.getStatusCode()) {
                    fail("Unauthorizd code expected");
                }
                exceptionsCounter++;
            } finally {
                accessKey.getPermissions().clear();
            }


        }
        assertEquals(methodCalls, exceptionsCounter);
    }

    private void actionTestProcess(AccessKey accessKey, String actions) throws Exception {
        Set<AccessKeyPermission> permissions = new HashSet<>();
        AccessKeyPermission permission = new AccessKeyPermission();
        permission.setAccessKey(accessKey);
        permission.setActions(new JsonStringWrapper(actions));
        permissions.add(permission);
        accessKey.setPermissions(permissions);
        interceptor.checkPermissions(context);
    }

    @Test
    public void subnetsCaseAllowed() {
        /**
         * Only subnets field and actions field are not null
         */
        AccessKey accessKey = new AccessKey();
        accessKey.setUser(CLIENT);
        for (Method method : allAvailableMethods) {
            when(context.getMethod()).thenReturn(method);
            AllowedKeyAction allowedActionAnnotation = method.getAnnotation(AllowedKeyAction.class);
            AllowedKeyAction.Action action = allowedActionAnnotation.action();
            try {
                HiveSecurityContext hiveSecurityContext = new HiveSecurityContext();
                hiveSecurityContext.setHivePrincipal(new HivePrincipal(null, null, accessKey));
                hiveSecurityContext.setOrigin("http://test.devicehive.com");
                try {
                    hiveSecurityContext.setClientInetAddress(InetAddress.getByName("8.8.8.8"));
                } catch (UnknownHostException e) {
                    fail("Unexpected exception");
                }
                interceptor.setHiveSecurityContext(hiveSecurityContext);
                subnetsTestProcess(accessKey, "[" + action.getValue() + "]");
            } catch (Exception e) {
                fail("No exceptions expected from interceptor");
            } finally {
                accessKey.getPermissions().clear();
            }
        }
    }

    @Test
    public void subnetsCaseNotAllowed() {
        /**
         * Only subnets field and actions field are not null
         */
        AccessKey accessKey = new AccessKey();
        accessKey.setUser(ADMIN);
        int exceptionsCounter = 0;
        for (Method method : allAvailableMethods) {
            when(context.getMethod()).thenReturn(method);
            AllowedKeyAction allowedActionAnnotation = method.getAnnotation(AllowedKeyAction.class);
            AllowedKeyAction.Action action = allowedActionAnnotation.action();
            try {
                HiveSecurityContext hiveSecurityContext = new HiveSecurityContext();
                hiveSecurityContext.setHivePrincipal(new HivePrincipal(null, null, accessKey));
                hiveSecurityContext.setOrigin("http://test.devicehive.com");
                try {
                    hiveSecurityContext.setClientInetAddress(InetAddress.getByName("192.150.1.1"));
                } catch (UnknownHostException e) {
                    fail("Unexpected exception");
                }
                interceptor.setHiveSecurityContext(hiveSecurityContext);
                try {
                    subnetsTestProcess(accessKey, "[" + action.getValue() + "]");
                } catch (HiveException e) {
                    if (e.getCode() != Response.Status.UNAUTHORIZED.getStatusCode()) {
                        fail("Unauthorizd code expected");
                    }
                    exceptionsCounter++;
                } finally {
                    accessKey.getPermissions().clear();
                }
            } catch (Exception e) {
                fail("No exceptions expected from interceptor");
            }
        }
        assertEquals(exceptionsCounter, methodCalls);
    }

    private void subnetsTestProcess(AccessKey accessKey, String action) throws Exception {
        Set<AccessKeyPermission> permissions = new HashSet<>();
        AccessKeyPermission permission = new AccessKeyPermission();
        permission.setAccessKey(accessKey);
        String subnets = "[\"8.8.0.0/8\", \"192.168.1.1/32\", \"78.7.3.5\"]";
        permission.setSubnets(new JsonStringWrapper(subnets));
        permission.setActions(new JsonStringWrapper(action));
        permissions.add(permission);
        accessKey.setPermissions(permissions);
        interceptor.checkPermissions(context);
    }

    @Test
    public void domainsCaseAllowed() {
        /**
         * Only subnets field and actions field are not null
         */
        AccessKey accessKey = new AccessKey();
        accessKey.setUser(CLIENT);
        for (Method method : allAvailableMethods) {
            when(context.getMethod()).thenReturn(method);
            AllowedKeyAction allowedActionAnnotation = method.getAnnotation(AllowedKeyAction.class);
            AllowedKeyAction.Action action = allowedActionAnnotation.action();
            try {
                HiveSecurityContext hiveSecurityContext = new HiveSecurityContext();
                hiveSecurityContext.setHivePrincipal(new HivePrincipal(null, null, accessKey));
                hiveSecurityContext.setOrigin("http://test.devicehive.com");
                try {
                    hiveSecurityContext.setClientInetAddress(InetAddress.getByName("8.8.8.8"));
                } catch (UnknownHostException e) {
                    fail("Unexpected exception");
                }
                interceptor.setHiveSecurityContext(hiveSecurityContext);
                domainsTestProcess(accessKey, "[" + action.getValue() + "]");
            } catch (Exception e) {
                fail("No exceptions expected from interceptor");
            } finally {
                accessKey.getPermissions().clear();
            }
        }
    }

    @Test
    public void domainsCaseNotAllowed() {
        /**
         * Only domains field and actions field are not null
         */
        AccessKey accessKey = new AccessKey();
        accessKey.setUser(ADMIN);
        int exceptionsCounter = 0;
        for (Method method : allAvailableMethods) {
            when(context.getMethod()).thenReturn(method);
            AllowedKeyAction allowedActionAnnotation = method.getAnnotation(AllowedKeyAction.class);
            AllowedKeyAction.Action action = allowedActionAnnotation.action();
            try {
                HiveSecurityContext hiveSecurityContext = new HiveSecurityContext();
                hiveSecurityContext.setHivePrincipal(new HivePrincipal(null, null, accessKey));
                hiveSecurityContext.setOrigin("http://test.devicehive.com.dataart.com");
                try {
                    hiveSecurityContext.setClientInetAddress(InetAddress.getByName("8.8.8.8"));
                } catch (UnknownHostException e) {
                    fail("Unexpected exception");
                }
                interceptor.setHiveSecurityContext(hiveSecurityContext);
                try {
                    domainsTestProcess(accessKey, "[" + action.getValue() + "]");
                } catch (HiveException e) {
                    if (e.getCode() != Response.Status.UNAUTHORIZED.getStatusCode()) {
                        fail("Unauthorizd code expected");
                    }
                    exceptionsCounter++;
                } finally {
                    accessKey.getPermissions().clear();
                }
            } catch (Exception e) {
                fail("No exceptions expected from interceptor");
            }
        }
        assertEquals(exceptionsCounter, methodCalls);
    }

    private void domainsTestProcess(AccessKey accessKey, String action) throws Exception {
        Set<AccessKeyPermission> permissions = new HashSet<>();
        AccessKeyPermission permission = new AccessKeyPermission();
        permission.setAccessKey(accessKey);
        String domains = "[\".net\", \"devicehive.com\"]";
        permission.setDomains(new JsonStringWrapper(domains));
        permission.setActions(new JsonStringWrapper(action));
        permissions.add(permission);
        accessKey.setPermissions(permissions);
        interceptor.checkPermissions(context);
    }
}


