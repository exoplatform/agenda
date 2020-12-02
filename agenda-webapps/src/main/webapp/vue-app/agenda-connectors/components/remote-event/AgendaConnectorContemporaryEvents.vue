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
      :connectors="connectors" />
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
      type: Object,
      default: () => ({})
    },
  },
  data() {
    return {
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
      return this.connectors.find(connector => connector.isSignedIn);
    },
    isConnectedConnectorEnabled() {
      return this.connectedConnector && this.connectedConnector.enabled;
    }
  },
  created() {
    this.connectedAccount = {
      connectorName: this.settings && this.settings.connectedRemoteProvider,
      userId: this.settings && this.settings.connectedRemoteUserId,
    };
    this.$root.$emit('agenda-init-connectors');
    this.$root.$on('agenda-connector-initialized', connectors => {
      this.connectors = connectors;
      this.retrieveRemoteEvents();
    });
    this.$root.$on('agenda-event-details-opened', () => {
      this.retrieveRemoteEvents();
    });
  },
  methods: {
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
      if(this.connectedConnector) {
        this.connectedConnector.getEvents(this.$agendaUtils.toRFC3339(eventStartDay, false),
          this.$agendaUtils.toRFC3339(eventEndDay, false))
          .then(events => {
            events.forEach(event => {
              event.startDate = event.start && this.$agendaUtils.toDate(event.start) || null;
              event.endDate = event.end && this.$agendaUtils.toDate(event.end) || null;
            });
            this.remoteEvents = events;
          }).catch(error => {
            console.error('Error retrieving remote events', error);
          });
      } else {
        this.remoteEvents = [];
      }
    },
  }
};
</script>