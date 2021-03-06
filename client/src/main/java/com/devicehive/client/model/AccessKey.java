package com.devicehive.client.model;


import com.devicehive.client.impl.json.strategies.JsonPolicyDef;

import java.sql.Timestamp;
import java.util.Set;

import static com.devicehive.client.impl.json.strategies.JsonPolicyDef.Policy.ACCESS_KEY_LISTED;
import static com.devicehive.client.impl.json.strategies.JsonPolicyDef.Policy.ACCESS_KEY_PUBLISHED;
import static com.devicehive.client.impl.json.strategies.JsonPolicyDef.Policy.ACCESS_KEY_SUBMITTED;
import static com.devicehive.client.impl.json.strategies.JsonPolicyDef.Policy.OAUTH_GRANT_LISTED;
import static com.devicehive.client.impl.json.strategies.JsonPolicyDef.Policy.OAUTH_GRANT_SUBMITTED_TOKEN;

/**
 * Represents an access key to this API. For more details see <a href="http://www.devicehive.com/restful#Reference/AccessKey">AccessKey</a>
 */
public class AccessKey implements HiveEntity {

    private static final long serialVersionUID = 5031432598347474481L;

    @JsonPolicyDef({ACCESS_KEY_LISTED, ACCESS_KEY_SUBMITTED, OAUTH_GRANT_SUBMITTED_TOKEN, OAUTH_GRANT_LISTED})
    private Long id;

    @JsonPolicyDef({ACCESS_KEY_LISTED, ACCESS_KEY_SUBMITTED, OAUTH_GRANT_SUBMITTED_TOKEN, OAUTH_GRANT_LISTED})
    private NullableWrapper<String> key;

    @JsonPolicyDef({ACCESS_KEY_LISTED, ACCESS_KEY_PUBLISHED, OAUTH_GRANT_LISTED})
    private NullableWrapper<String> label;

    @JsonPolicyDef({ACCESS_KEY_LISTED, ACCESS_KEY_PUBLISHED, OAUTH_GRANT_LISTED})
    private NullableWrapper<Timestamp> expirationDate;

    @JsonPolicyDef({ACCESS_KEY_LISTED, ACCESS_KEY_PUBLISHED, OAUTH_GRANT_LISTED})
    private NullableWrapper<Set<AccessKeyPermission>> permissions;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabel() {
        return NullableWrapper.value(label);
    }

    public void setLabel(String label) {
        this.label = NullableWrapper.create(label);
    }

    public void removeLabel(String label) {
        this.label = null;
    }

    public Timestamp getExpirationDate() {
        return NullableWrapper.value(expirationDate);
    }

    public void setExpirationDate(Timestamp expirationDate) {
        this.expirationDate = NullableWrapper.create(expirationDate);
    }

    public Set<AccessKeyPermission> getPermissions() {
        return NullableWrapper.value(permissions);
    }

    public void setPermissions(Set<AccessKeyPermission> permissions) {
        this.permissions = NullableWrapper.create(permissions);
    }

    public void removePermissions() {
        this.permissions = null;
    }

    public String getKey() {
        return NullableWrapper.value(key);
    }

    public void setKey(String key) {
        this.key = new NullableWrapper<>(key);
    }

    public void removeKey() {
        this.key = null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AccessKey{");
        sb.append("id=").append(id);
        sb.append(", label=").append(label);
        sb.append(", expirationDate=").append(expirationDate);
        sb.append('}');
        return sb.toString();
    }
}
