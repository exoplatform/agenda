<script>
export default {
  props: {
    connectors: {
      type: Array,
      default: () => []
    }
  },
  data: () => ({
    loading: false,
    connectedAccount: {}
  }),
  watch: {
    loading(newValue, oldValue) {
      if (this.loading) {
        if (!oldValue) {
          document.dispatchEvent(new CustomEvent('displayTopBarLoading'));
        }
      } else {
        if (!newValue) {
          document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
        }
      }
    }
  },
  mounted() {
    // Retrieving list of registered connectors from extensionRegistry
    document.addEventListener('agenda-accounts-connectors-refresh', this.refreshConnectorsList);
    this.refreshConnectorsList();
    this.retrieveConnectedConnectorSettings();
    this.$root.$on('agenda-init-connectors', this.initConnectors);
    this.$root.$on('agenda-synchronize-current-connector',
      (connector,currentUser) => {
        this.connectionStatusChanged(connector, currentUser);
      });
  },
  methods: {
    refreshConnectorsList() {
      this.connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];
      this.$root.$emit('agenda-connector-loaded', this.connectors);
    },
    initConnectors() {
      this.connectors.forEach(connector => {
        if (connector.init && !connector.initialized) {
          connector.init(this.connectionStatusChanged, this.connectionLoading);
        }
      });
    },
    retrieveConnectedConnectorSettings() {
      return this.$calendarService.getAgendaConnectorsSettings().then(connectorSettings => {
        if (connectorSettings && connectorSettings.value) {
          this.connectedAccount = JSON.parse(connectorSettings.value);
        }
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
        //connector.user = this.connectedAccount.userId;
        //if user connected with different account from other browser
        if (currentUser.user && currentUser.user !== this.connectedAccount.userId) {
          connector.disconnect();
        }
        //if user disconnected from other browser
      } else if (!this.connectedAccount.userId && connector.isSignedIn) {
        connector.disconnect();
      } else {
        //connector.user = currentUser && currentUser.user || null;
        this.$set(connector, 'user', currentUser && currentUser.user || null);
      }
    },
  },
};
</script>