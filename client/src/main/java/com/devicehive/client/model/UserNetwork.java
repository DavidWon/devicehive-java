package com.devicehive.client.model;

import com.devicehive.client.impl.json.strategies.JsonPolicyDef;

import static com.devicehive.client.impl.json.strategies.JsonPolicyDef.Policy.NETWORKS_LISTED;
import static com.devicehive.client.impl.json.strategies.JsonPolicyDef.Policy.USER_PUBLISHED;

/**
 * User-Network association
 */
public class UserNetwork implements HiveEntity {

    private static final long serialVersionUID = 5320582614135741990L;
    @JsonPolicyDef({NETWORKS_LISTED, USER_PUBLISHED})
    private Network network;

    public UserNetwork() {
    }

    public Network getNetwork() {
        return network;
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("UserNetwork{");
        sb.append("network=").append(network);
        sb.append('}');
        return sb.toString();
    }
}
