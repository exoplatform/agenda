<template>
  <div>
    <div class="d-flex">
      <v-avatar
        v-if="connectedAccountName && isConnectedConnectorEnabled"
        tile
        class="mr-2 pt-1"
        size="32">
        <img
          :alt="connectedAccount.name"
          :src="connectedAccountIconSource">
      </v-avatar>
      <i v-else class="uiIconCalRemoteCalendar darkGreyIcon uiIcon24x24 pr-2 pt-2"></i>
      <div class="d-flex flex-column">
        <div class="d-flex">
          {{ $t('agenda.personalCalendar') }}
          {{ ' ( ' }}
          <date-format
            :value="event.startDate"
            :format="fullDateFormat"
            class="mr-1" />
          {{ ' ) ' }}
        </div>
        <a
          v-if="connectedAccountName && isConnectedConnectorEnabled"
          @click="openPersonalCalendarDrawer">
          {{ connectedAccountName }}
        </a>
        <a
          v-else
          @click="openPersonalCalendarDrawer">
          {{ $t('agenda.connectYourPersonalAgendaSubTitle') }}
        </a>
        <agenda-connector-remote-event-item
          v-for="remoteEvent in remoteEvents"
          :key="remoteEvent"
          class="mt-5"
          is-events-list
          :remote-event="remoteEvent"
          :connected-account-icon-source="connectedAccountIconSource" />
      </div>
    </div>
    <agenda-user-connected-account-drawer
      :connected-account="connectedAccount"
      :connectors="connectors"
      @updated="connectedAccount = $event" />
  </div>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null
    },
    event: {
      type: Object,
      default: () => ({})
    },
    connectors: {
      type: Array,
      default: () => []
    },
  },
  data() {
    return {
      initialized: false,
      remoteEvents: [],
      connectedAccount: {},
      fullDateFormat: {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      },
      timeFormat: {
        hour: '2-digit',
        minute: '2-digit',
      },
    };
  },
  computed: {
    connectedAccountName() {
      return this.connectedAccount && this.connectedAccount.userId || '';
    },
    connectedAccountIconSource() {
      return this.connectedAccount && this.connectedAccount.icon || '';
    },
    connectedConnector() {
      return this.connectedAccount && this.connectors.find(connector => connector.user === this.connectedAccount.userId);
    },
    isConnectedConnectorEnabled() {
      return this.connectedConnector && this.connectedConnector.enabled;
    }
  },
  watch: {
    settings() {
      this.refresh();
    },
  },
  created() {
    this.$root.$on('agenda-connector-initialized', connectors => {
      this.connectors = connectors;
      this.refresh();
      this.initialized = true;
      this.retrieveRemoteEvents();
    });
    this.$root.$on('agenda-event-details-opened', () => {
      if (this.initialized) {
        this.retrieveRemoteEvents();
      }
    });
    this.$root.$emit('agenda-init-connectors');
  },
  methods: {
    refresh() {
      const connectorName = (this.connectedAccount && this.connectedAccount.connectorName) || (this.settings && this.settings.connectedRemoteProvider);
      const connectorObj = this.connectors && this.connectors.find(connector => connector.name === connectorName);
      if (connectorObj) {
        this.connectedAccount = {
          connectorName: connectorObj.name,
          userId: connectorObj.user || (this.settings && this.settings.connectedRemoteUserId),
          icon: connectorObj.avatar,
        };
      } else {
        this.connectedAccount = {
          connectorName: this.settings && this.settings.connectedRemoteProvider,
          userId: this.settings && this.settings.connectedRemoteUserId,
        };
      }
    },
    openPersonalCalendarDrawer() {
      this.$root.$emit('agenda-connected-account-settings-open');
    },
    retrieveRemoteEvents() {
      const eventStartDay = this.event.startDate;
      const eventEndDay = this.event.endDate;
      // Start of the day of start date
      eventStartDay.setHours(0);
      eventStartDay.setMinutes(0);
      // End of the day of end date
      eventEndDay.setHours(23);
      eventEndDay.setMinutes(59);
      if(this.connectedConnector && this.connectedConnector.user) {
        this.connectedConnector.getEvents(this.$agendaUtils.toRFC3339(eventStartDay, false, true),
          this.$agendaUtils.toRFC3339(eventEndDay, false, true))
          .then(events => {
            events.forEach(event => {
              event.startDate = event.start && this.$agendaUtils.toDate(event.start) || null;
              event.endDate = event.end && this.$agendaUtils.toDate(event.end) || null;
            });
            this.remoteEvents = events;
          }).catch(error => {
            this.$root.$emit('hideRemoteEventLoading');
            this.remoteEvents = [];
            console.error('Error retrieving remote events', error);
          });
      } else {
        this.remoteEvents = [];
      }
    },
  }
};
</script>