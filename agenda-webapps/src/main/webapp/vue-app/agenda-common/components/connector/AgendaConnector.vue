<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null,
    },
    connectors: {
      type: Object,
      default: () => null,
    },
  },
  data: () => ({
    loading: false,
  }),
  computed: {
    remoteProviders() {
      return this.settings && this.settings.remoteProviders;
    },
    connectedConnectorUser() {
      return this.connectedConnector && this.connectedConnector.user;
    },
    connectedConnector() {
      return this.connectors && this.connectors.find(connector => connector.connected);
    },
  },
  watch: {
    remoteProviders() {
      if (this.remoteProviders) {
        this.refreshConnectorsList();
      }
    },
  },
  created() {
    // Retrieving list of registered connectors from extensionRegistry
    document.addEventListener('agenda-connectors-refresh', this.refreshConnectorsList);
    this.$root.$on('agenda-connectors-init', this.initConnectors);
    this.$root.$on('agenda-connector-connect', this.connect);
    this.$root.$on('agenda-connector-disconnect', this.disconnect);
    this.$root.$on('agenda-event-saved', this.pushEvent);
    this.$root.$on('agenda-event-deleted', this.deleteEvent);
    this.$root.$on('agenda-event-response-updated', this.pushEventResponse);
  },
  mounted() {
    this.refreshConnectorsList();
  },
  methods: {
    refreshConnectorsList() {
      // Get list of connectors from extensionRegistry
      const connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];

      // Check connectors 'enablement' status from store
      if (this.remoteProviders && this.remoteProviders.length) {
        connectors
          .forEach(connector => {
            const connectorObj = this.remoteProviders.find(connectorSettings => connectorSettings.name === connector.name);
            connector.enabled = connectorObj && connectorObj.enabled || false;
            connector.apiKey = connectorObj && connectorObj.apiKey || '';
            connector.connected = connector.enabled && this.settings.connectedRemoteProvider === connectorObj.name;
            connector.user = connector.connected && this.settings.connectedRemoteUserId || '';
          });
      } else {
        connectors.forEach(connector => connector.enabled = false);
      }

      this.initConnectors();

      this.$emit('connectors-loaded', connectors);
    },
    initConnectors() {
      this.connectors
        .forEach(connector => {
          if (connector.init && !connector.initialized && connector.enabled && connector.apiKey) {
            connector.init(this.connectionStatusChanged, this.connectionLoading, connector.apiKey);
          }
        });
    },
    connect(connector) {
      this.errorMessage = null;
      this.$set(connector, 'loading', true);
      return connector.connect(this.settings && this.settings.automaticPushEvents)
        .then((userId) => this.$settingsService.saveUserConnector(connector.name, userId))
        .then(() => {
          this.$set(connector, 'loading', false);
          this.$root.$emit('agenda-settings-refresh');
        })
        .catch(error => {
          this.$set(connector, 'loading', false);
          if(error.error !== 'popup_closed_by_user') {
            console.error('Error while connecting to remote account: ', error);
            this.errorMessage = this.$t('agenda.connectionFailure');
          }
        });
    },
    disconnect(connector) {
      //disconnect from connected browser
      if (connector.isSignedIn) {
        return connector.disconnect().then(() => this.resetConnector(connector));
      } else {//disconnect from other browser
        return this.resetConnector(connector);
      }
    },
    resetConnector(connector) {
      this.$set(connector, 'loading', true);
      this.$set(connector, 'error', '');
      return this.$settingsService.resetUserConnector()
        .then(() => this.$root.$emit('agenda-settings-refresh'))
        .finally(() => {
          this.$set(connector, 'loading', false);
        });
    },
    connectionLoading(connector, loading) {
      this.$set(connector, 'loading', loading);

      if (loading) {
        this.$set(connector, 'error', '');
        this.loading++;
      } else if (this.loading) {
        this.loading--;
      }
    },
    connectionStatusChanged(connector, connectedUser, error) {
      if (connectedUser) {
        this.$set(connector, 'error', '');
        this.$root.$emit('agenda-connector-connected', connector);
      } else if (error) {
        const errorMessage = error.details || error.error || error.message || String(error);
        this.$set(connector, 'error', errorMessage);
      } else {
        this.cleanConnectorStatus(connector, connectedUser);
      }
    },
    cleanConnectorStatus(connector, connectedUser) {
      this.$set(connector, 'error', '');

      if (this.connectedConnectorUser) {
        //if user is connected with different account from other browser
        if (connectedUser && connectedUser.user !== this.connectedConnectorUser) {
          connector.disconnect();
        }
      } else if (connector && connector.isSignedIn) {
        //if user disconnected from other browser
        connector.disconnect();
      }

      this.refreshConnectorsList();
    },
    deleteEvent(event) {
      if (this.settings && this.settings.automaticPushEvents && this.connectedConnector && this.connectedConnector.canPush) {
        return this.$remoteEventConnector.removeEventFromConnector(this.connectedConnector, event, !!event.recurrence)
          .finally(() => this.$root.$emit('agenda-refresh'));
      }
    },
    pushEvent(event) {
      if (event && event.acl && event.acl.attendee) {
        const userAttendee = event.attendees.find(user => user.identity && user.identity.id === eXo.env.portal.userIdentityId);
        this.pushEventResponse(event, null, userAttendee && userAttendee.response);
      }
    },
    pushEventResponse(event, occurrenceId, eventResponse) {
      if (eventResponse && this.settings && this.settings.automaticPushEvents && this.connectedConnector && this.connectedConnector.canPush) {
        if (eventResponse.toLowerCase()  === 'accepted') {
          return this.$remoteEventConnector.pushEventToConnector(this.connectedConnector, event, !!event.recurrence)
            .finally(() => this.$root.$emit('agenda-refresh'));
        } else {
          return this.$remoteEventConnector.removeEventFromConnector(this.connectedConnector, event, !!event.recurrence)
            .finally(() => this.$root.$emit('agenda-refresh'));
        }
      }
    },
  },
};
</script>