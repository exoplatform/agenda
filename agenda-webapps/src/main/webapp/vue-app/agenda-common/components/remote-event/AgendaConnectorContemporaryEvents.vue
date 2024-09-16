<template>
  <div>
    <div class="d-flex">
      <div class="d-flex flex-column width-full">
        <div class="d-flex text-no-wrap text-truncate font-weight-bold text-title-color">
          {{ $t('agenda.personalCalendar') }}
        </div>
        <div :class="{ 'd-flex': enabledconnectors }">
          <v-avatar
            v-if="connectedConnector"
            tile
            class="me-1"
            size="16">
          <img :src="connectedConnectorAvatar">
          </v-avatar>
          <a
            v-if="connectedConnector"
            @click="openPersonalCalendarDrawer"
            class="icon-small-size">
            {{ connectedConnectorUser }}
          </a>
          <div
            v-else
            class="text-subtitle d-block">
            <div>
              {{ $t('agenda.synchronizeEventsWithPersonalCalendarSubTitle') }}
            </div>
            <div class="text-center">
              <v-btn
                class="btn primary-border-color primary--text remote-event mt-3 justify-content-center"
                @click="openPersonalCalendarDrawer">
                {{ $t('agenda.connect') }}
              </v-btn>
           </div>
          </div>
        </div>
        <template v-if="loading || connectedConnectorLoading">
          <v-progress-linear indeterminate />
        </template>
        <template v-if="connectedConnector">
          <template v-if="hasRemoteEvents">
            <agenda-connector-remote-event-item
              v-for="remoteEvent in displayedRemoteEvents"
              :key="remoteEvent"
              :remote-event="remoteEvent"
              :avatar="connectedConnectorAvatar"
              :event="event"
              class="mt-5 remote-events-details"
              is-events-list />
          </template>
          <v-chip
            v-else-if="connectedConnectorSignedOut"
            color="primary"
            class="border-radius my-2"
            outlined>
            <v-icon
              size="20"
              class="me-4"
              color="primary"
              depressed>
              fa-info-circle
            </v-icon>
            <span class="text--primary text-wrap">
              {{ $t('agenda.signedOutConnector') }}
            </span>
          </v-chip>
          <v-chip
            v-else-if="!connectedConnector.loading && !loading"
            color="primary"
            class="border-radius my-2"
            outlined>
            <v-icon
              size="20"
              class="me-4"
              color="primary"
              depressed>
              fa-info-circle
            </v-icon>
            <span class="text--primary text-wrap">
              {{ $t('agenda.noRemoteEvents') }}
            </span>
          </v-chip>
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
    connectorStatus() {
      if (this.connectedConnector) {
        if (this.connectedConnector.isSignedIn) {
          return 1;
        } else {
          return 2;
        }
      } else {
        return 0;
      }
    },
    connectedConnectorUser() {
      return this.connectedConnector && this.connectedConnector.user || '';
    },
    connectedConnectorAvatar() {
      return this.connectedConnector && this.connectedConnector.avatar || '';
    },
    connectedConnectorLoading() {
      return this.connectedConnector && this.connectedConnector.loading;
    },
    connectedConnectorSignedOut() {
      return this.connectedConnector && !this.connectedConnector.isSignedIn && !this.connectedConnectorLoading && !this.loading;
    },
    hasRemoteEvents() {
      return this.displayedRemoteEvents && this.displayedRemoteEvents.length;
    },
    displayedRemoteEvents() {
      const remoteEventsToDisplay = this.remoteEvents && this.remoteEvents.slice() || [];
      // Avoid to have same event from remote and local store (pushed events from local store)
      if (remoteEventsToDisplay.length) {
        const index = remoteEventsToDisplay.findIndex(remoteEvent => remoteEvent.id && remoteEvent.id === this.event.remoteId || remoteEvent.recurringEventId === this.event.remoteId || (this.event.parent && remoteEvent.recurringEventId === this.event.parent.remoteId));
        if (index >= 0) {
          remoteEventsToDisplay.splice(index, 1);
        }
        remoteEventsToDisplay.push(this.event);
        remoteEventsToDisplay.sort((event1, event2) => {
          const eventStart1 = this.$agendaUtils.toDate(event1.start || event1.startDate).getTime();
          const eventStart2 = this.$agendaUtils.toDate(event2.start || event2.startDate).getTime();
          return eventStart1 - eventStart2;
        });
      }
      return remoteEventsToDisplay;
    },
  },
  watch: {
    connectorStatus() {
      this.refreshRemoteEvents();
    },
  },
  created() {
    this.$root.$emit('agenda-connectors-init');
    this.refreshRemoteEvents();
  },
  methods: {
    openPersonalCalendarDrawer() {
      this.$root.$emit('agenda-connectors-drawer-open');
    },
    refreshRemoteEvents() {
      this.retrieveRemoteEvents(this.connectedConnector);
    },
    retrieveRemoteEvents(connector) {
      if (this.connectorStatus === 1) {
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
