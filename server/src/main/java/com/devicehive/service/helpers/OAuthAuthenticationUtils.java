package com.devicehive.service.helpers;

import com.devicehive.configuration.Messages;
import com.devicehive.configuration.PropertiesService;
import com.devicehive.exceptions.HiveException;
import com.devicehive.json.adapters.TimestampAdapter;
import com.devicehive.model.*;
import com.devicehive.service.DeviceService;
import com.devicehive.service.IdentityProviderService;
import com.devicehive.service.NetworkService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tmatvienko on 11/21/14.
 */
@Stateless
public class OAuthAuthenticationUtils {

    public static final String DEFAULT_EXPIRATION_DATE_PROPERTY = "access.key.default.expiration.date";
    public static final String OAUTH_ACCESS_KEY_LABEL_FORMAT = "%s OAuth token for: %s";

    @EJB
    private NetworkService networkService;
    @EJB
    private DeviceService deviceService;
    @EJB
    private PropertiesService propertiesService;
    @EJB
    private IdentityProviderService identityProviderService;

    private Long googleIdentityProviderId;
    private Long facebookIdentityProviderId;

    @PostConstruct
    public void loadProperties() {
        googleIdentityProviderId = Long.valueOf(propertiesService.getProperty("google.identity.provider.id"));
        facebookIdentityProviderId = Long.valueOf(propertiesService.getProperty("facebook.identity.provider.id"));
    }

    public AccessKey prepareAccessKey(final User user, final String providerName, final String email) {
        AccessKey accessKey = new AccessKey();
        accessKey.setUser(user);
        accessKey.setLabel(String.format(OAUTH_ACCESS_KEY_LABEL_FORMAT, providerName, email));
        AccessKeyProcessor keyProcessor = new AccessKeyProcessor();
        accessKey.setKey(keyProcessor.generateKey());
        Timestamp expirationDate = TimestampAdapter.parseTimestamp(propertiesService.getProperty(DEFAULT_EXPIRATION_DATE_PROPERTY));
        accessKey.setExpirationDate(expirationDate);
        return accessKey;
    }

    public AccessKeyPermission preparePermission() {
        AccessKeyPermission permission = new AccessKeyPermission();
        permission.setActions(AvailableActions.getAllActions());
        return permission;
    }

    public IdentityProvider getIdentityProvider(final String state) {
        if (!state.startsWith("identity_provider_id")) {
            throw new HiveException(Messages.INVALID_REQUEST_PARAMETERS,
                    Response.Status.BAD_REQUEST.getStatusCode());
        }
        final int index = state.indexOf("=") + 1;
        final String identityProviderId = state.substring(index, state.length());
        if (!StringUtils.isNumeric(identityProviderId)) {
            throw new HiveException(Messages.INVALID_REQUEST_PARAMETERS,
                    Response.Status.BAD_REQUEST.getStatusCode());
        }
        final Long providerId = Long.valueOf(identityProviderId);
        final IdentityProvider identityProvider = identityProviderService.find(providerId);
        if (identityProvider == null) {
            throw new HiveException(Messages.INVALID_REQUEST_PARAMETERS,
                    Response.Status.BAD_REQUEST.getStatusCode());
        }
        return identityProvider;
    }

    public boolean validateVerificationResponse(final String response, final IdentityProvider identityProvider) {
        final JsonObject jsonObject = new JsonParser().parse(response).getAsJsonObject();
        if (jsonObject.get("error") != null) {
            throw new HiveException(String.format(Messages.OAUTH_ACCESS_TOKEN_VERIFICATION_FAILED, identityProvider.getName()),
                    Response.Status.FORBIDDEN.getStatusCode());
        }
        JsonElement verificationElement;
        final Long providerId = identityProvider.getId();
        if (providerId.equals(googleIdentityProviderId)) {
            verificationElement = jsonObject.get("issued_to");
            return verificationElement != null && verificationElement.getAsString().startsWith(propertiesService.getProperty("google.identity.client.id"));
        } else if (providerId.equals(facebookIdentityProviderId)) {
            verificationElement = jsonObject.get("id");
            return verificationElement != null && verificationElement.getAsString().equals(propertiesService.getProperty("facebook.identity.client.id"));
        }
        throw new HiveException(String.format(Messages.IDENTITY_PROVIDER_NOT_FOUND, identityProvider.getId()),
                Response.Status.BAD_REQUEST.getStatusCode());
    }

    public String getEmailFromResponse (final String response, @NotNull final Long providerId) throws HiveException {
        if (StringUtils.isBlank(response)) {
            return null;
        }
        final JsonObject jsonObject = new JsonParser().parse(response).getAsJsonObject();
        if (providerId.equals(googleIdentityProviderId)) {
            final JsonArray jsonArray = jsonObject.getAsJsonArray("emails");
            if (jsonArray != null && jsonArray.size() > 0) {
                return jsonArray.get(0).getAsJsonObject().get("value").getAsString();
            }
        } else if (providerId.equals(facebookIdentityProviderId)) {
            final JsonElement jsonElement = jsonObject.get("email");
            if (jsonElement != null) {
                return jsonObject.get("email").getAsString();
            }
        }
        throw new HiveException(Messages.WRONG_IDENTITY_PROVIDER_SCOPE, Response.Status.BAD_REQUEST.getStatusCode());
    }

    public void validateActions(AccessKey accessKey) {
        Set<String> actions = new HashSet<>();
        for (AccessKeyPermission permission : accessKey.getPermissions()) {
            if (permission.getActionsAsSet() == null) {
                throw new HiveException(Messages.ACTIONS_ARE_REQUIRED, Response.Status.BAD_REQUEST.getStatusCode());
            }
            actions.addAll(permission.getActionsAsSet());
        }
        if (!AvailableActions.validate(actions)) {
            throw new HiveException(Messages.UNKNOWN_ACTION, Response.Status.BAD_REQUEST.getStatusCode());
        }
    }
}
