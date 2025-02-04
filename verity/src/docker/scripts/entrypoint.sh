#! /bin/bash

trap stop_sig SIGINT SIGTERM SIGQUIT SIGABRT

function stop_sig {
    echo "Caught a shutdown signal..."
    if [ ! -z "$VERITY_PID" ] ; then
        echo "Signaling Verity to stop gracefully"
        kill -TERM $VERITY_PID
    fi
}

VAULT_SECRETS=("/vault/secrets/credentials-tf.json" "/vault/secrets/credentials.json" "/vault/secrets/app-confluent.json")

# Source secret files injected from Vault (if they exist)
for f in "${VAULT_SECRETS[@]}" ; do
    if [ -f $f ]; then
        tmp_conf_file=$(mktemp /tmp/XXXXXXXX)
        python3 /usr/local/bin/json_to_config.py -j $f -c $tmp_conf_file
        source $tmp_conf_file
        rm $tmp_conf_file
    fi
done

# Start Verity
/usr/bin/java \
    -javaagent:/usr/lib/verity-application/${METRICS_AGENT:-kanela-agent}.jar \
    -Dlogback.statusListenerClass=ch.qos.logback.core.status.OnConsoleStatusListener \
    -cp /etc/verity/verity-application:/etc/verity/verity-application/config-map:/usr/lib/verity-application/verity-assembly.jar \
    com.evernym.verity.Main &
VERITY_PID=$!

wait $VERITY_PID
trap - SIGINT SIGTERM SIGQUIT SIGABRT
wait $VERITY_PID
