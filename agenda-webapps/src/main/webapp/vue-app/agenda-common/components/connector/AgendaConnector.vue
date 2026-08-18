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
    /**
     * Connects a connector to its remote account: disconnects any other
     * connected connector, delegates the connection to the connector itself,
     * then stores the connected user. Once the connection succeeded, when the
     * connector can create a calendar on the remote server, the mirror
     * calendar step is offered — the destination the pushed meetings will be
     * written to — so a refused creation is dealt with at connect time, not
     * at first push.
     *
     * @param {Object} connector the connector to connect
     * @returns {Promise} resolves once the connection is established and stored
     */
    connect(connector) {
      this.errorMessage = null;

      const disconnectPromises = [];
      this.connectors.forEach(otherConnector => {
        if (connector.name !== otherConnector.name && (otherConnector.user || otherConnector.isSignedIn)) {
          disconnectPromises.push(this.disconnect(otherConnector));
        }
      });

      this.$set(connector, 'loading', true);
      return Promise.all(disconnectPromises)
        .then(() => connector.connect(this.settings && this.settings.automaticPushEvents))
        .then((userId) => {
          return this.$settingsService.saveUserConnector(connector.name, userId)
            .then(() => {
              this.$set(connector, 'isSignedIn', true);
              this.$set(connector, 'user', userId);
              // Marked connected here rather than left to the settings to say
              // so. That flag is derived from the settings held by this page,
              // and the refresh asked for below only replaces them later — so
              // between connecting and that arriving, nothing counted as
              // connected and every event saved in the meantime was silently
              // not pushed. Disconnecting already clears the flag directly;
              // this is the other half of it.
              this.$set(connector, 'connected', true);
            });
        })
        .then(() => {
          this.$set(connector, 'loading', false);
          this.$root.$emit('agenda-settings-refresh');
          this.refreshConnectorsList();
          if (connector.canCreateCalendar) {
            this.$root.$emit('agenda-connector-mirror-calendar-open', connector);
          }
        })
        .catch(error => {
          console.error('Connected - error', connector.name, error);

          this.$set(connector, 'loading', false);
          if (error.error !== 'popup_closed_by_user') {
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
      return this.$settingsService.resetUserConnector()
        .then(() => {
          this.$set(connector, 'isSignedIn', false);
          this.$set(connector, 'connected', false);
          this.$set(connector, 'user', null);
          // canPush is deliberately left alone. It says the connector is able
          // to copy events, which stays true of the connector whether or not
          // an account is attached — and the connectors are the shared
          // objects the registry hands out, so clearing it here turned the
          // ability off for the rest of the page's life. Disconnecting once
          // then stopped every later push, reconnecting did not bring it
          // back, and only a reload did. Whether to copy anything is already
          // decided by there being a connected connector at all.
          this.$root.$emit('agenda-settings-refresh');
          this.refreshConnectorsList();
        })
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
      if (event && eventResponse && this.settings && this.settings.automaticPushEvents && this.connectedConnector && this.connectedConnector.canPush) {
        event.start = this.$agendaUtils.toRFC3339(event.start);
        event.end = this.$agendaUtils.toRFC3339(event.end);

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