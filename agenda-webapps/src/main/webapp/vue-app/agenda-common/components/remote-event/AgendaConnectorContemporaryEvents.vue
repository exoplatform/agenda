<template>
  <div>
    <div class="d-flex">
      <v-avatar
        v-if="connectedConnector"
        tile
        class="mr-2 pt-1"
        size="32">
        <img :src="connectedConnectorAvatar">
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
          v-if="connectedConnector"
          @click="openPersonalCalendarDrawer">
          {{ connectedConnectorUser }}
        </a>
        <a
          v-else
          @click="openPersonalCalendarDrawer">
          {{ $t('agenda.connectYourPersonalAgendaSubTitle') }}
        </a>
        <template v-if="connectedConnector">
          <template v-if="remoteEvents && remoteEvents.length">
            <agenda-connector-remote-event-item
              v-for="remoteEvent in remoteEvents"
              :key="remoteEvent"
              class="mt-5"
              is-events-list
              :remote-event="remoteEvent"
              :avatar="connectedConnectorAvatar" />
          </template>
          <template v-else-if="connectedConnectorSignedOut">
            <v-alert type="info">
              {{ $t('agenda.signedOutConnector') }}
            </v-alert>
          </template>
          <template v-else-if="!connectedConnector.loading && !loading">
            <v-alert type="info">
              {{ $t('agenda.noRemoteEvents') }}
            </v-alert>
          </template>
        </template>
      </div>
    </div>
    <agenda-connectors-drawer :connectors="connectors" />
  </div>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null
    },
    connectors: {
      type: Array,
      default: () => null
    },
    event: {
      type: Object,
      default: () => ({})
    },
  },
  data() {
    return {
      loading: false,
      remoteEvents: [],
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
    connectedConnector() {
      return this.connectors && this.connectors.find(connector => connector.connected);
    },
    connectedConnectorUser() {
      return this.connectedConnector && this.connectedConnector.user || '';
    },
    connectedConnectorAvatar() {
      return this.connectedConnector && this.connectedConnector.avatar || '';
    },
    connectedConnectorSignedOut() {
      return this.connectedConnector && !this.connectedConnector.isSignedIn && !this.connectedConnector.loading && !this.loading;
    },
  },
  watch: {
    loading() {
      if (this.loading) {
        this.$root.$emit('displayRemoteEventLoading');
      } else {
        this.$root.$emit('hideRemoteEventLoading');
      }
    },
  },
  created() {
    this.$root.$on('agenda-event-details-opened', () => {
      this.$root.$emit('agenda-connectors-init');
      this.refreshRemoteEvents();
    });
    this.$root.$on('agenda-connector-connected', this.retrieveRemoteEvents);
  },
  methods: {
    openPersonalCalendarDrawer() {
      this.$root.$emit('agenda-connectors-drawer-open');
    },
    refreshRemoteEvents() {
      this.retrieveRemoteEvents(this.connectedConnector);
    },
    retrieveRemoteEvents(connector) {
      if(connector) {
        const eventStartDay = this.event.startDate;
        const eventEndDay = this.event.endDate;

        // Start of the day of start date
        eventStartDay.setHours(0);
        eventStartDay.setMinutes(0);

        // End of the day of end date
        eventEndDay.setHours(23);
        eventEndDay.setMinutes(59);

        const startDateRFC3359 = this.$agendaUtils.toRFC3339(eventStartDay, false, true);
        const endDateRFC3359 = this.$agendaUtils.toRFC3339(eventEndDay, false, true);

        this.loading = true;
        connector.getEvents(startDateRFC3359, endDateRFC3359)
          .then(events => {
            if (events) {
              events.forEach(event => {
                event.startDate = event.start && this.$agendaUtils.toDate(event.start) || null;
                event.endDate = event.end && this.$agendaUtils.toDate(event.end) || null;
              });
            }
            this.remoteEvents = events || [];
            this.loading = false;
          }).catch(error => {
            this.remoteEvents = [];
            this.loading = false;
            console.error('Error retrieving remote events', error);
          });
      } else {
        this.remoteEvents = [];
      }
    },
  }
};
</script>