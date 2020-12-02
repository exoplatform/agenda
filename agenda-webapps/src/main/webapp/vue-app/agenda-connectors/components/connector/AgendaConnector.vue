<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null,
    }
  },
  data: () => ({
    loading: false,
    connectors: [],
    connectedAccount: {},
  }),
  watch: {
    loading(newValue, oldValue) {
      if (this.loading) {
        if (!oldValue) {
          this.$root.$emit('displayRemoteEventLoading');
        }
      } else {
        if (!newValue) {
          this.$root.$emit('hideRemoteEventLoading');
        }
      }
    }
  },
  mounted() {
    // Retrieving list of registered connectors from extensionRegistry
    document.addEventListener('agenda-accounts-connectors-refresh', this.refreshConnectorsList);
    this.refreshConnectorsList();
    this.$root.$on('agenda-init-connectors', this.initConnectors);
    this.$root.$on('agenda-synchronize-current-connector',
      (connector,currentUser) => {
        this.connectionStatusChanged(connector, currentUser);
      });
  },
  methods: {
    refreshConnectorsList() {
      const connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];

      // Check connectors status from store
      if (this.settings.connectors) {
        connectors.forEach(connector => {
          const connectorObj = this.settings.connectors.find(connectorSettings => connectorSettings.name === connector.name);
          connector.enabled = connectorObj && connectorObj.enabled || false;
        });
      } else {
        connectors.forEach(connector => {
          connector.enabled = false;
        });
      }

      this.connectors = connectors;
      this.$root.$emit('agenda-connector-loaded', this.connectors);
    },
    initConnectors() {
      this.connectors.forEach(connector => {
        if (connector.init && !connector.initialized && connector.enabled) {
          connector.init(this.connectionStatusChanged, this.connectionLoading);
        }
      });
    },
    retrieveConnectedConnectorSettings() {
      return this.$settingsService.getUserSettings()
        .then(settings => {
          this.connectedAccount = {
            connectorName: settings && settings.connectedRemoteProvider,
            userId: settings && settings.connectedRemoteUserId,
          };
        });
    },
    connectionLoading(connector, loading) {
      this.$set(connector, 'loading', loading);
      if (loading) {
        this.loading++;
      } else if (this.loading) {
        this.loading--;
      }
    },
    connectionStatusChanged(connector, currentUser) {
      this.retrieveConnectedConnectorSettings().then(() => {
        this.refreshConnectorStatus(connector, currentUser);
        this.connectedConnector = connector;
      });
    },
    refreshConnectorStatus(connector, currentUser) {
      //if user has connected
      if (this.connectedAccount.userId) {
        this.$set(connector, 'user', this.connectedAccount.userId);
        //if user connected with different account from other browser
        if (currentUser.user && currentUser.user !== this.connectedAccount.userId) {
          connector.disconnect();
        }
        //if user disconnected from other browser
      } else if (!this.connectedAccount.userId && connector.isSignedIn) {
        connector.disconnect();
      } else {
        this.$set(connector, 'user', currentUser && currentUser.user || null);
      }
      this.$root.$emit('agenda-connector-initialized', this.connectors);
    },
  },
};
</script>