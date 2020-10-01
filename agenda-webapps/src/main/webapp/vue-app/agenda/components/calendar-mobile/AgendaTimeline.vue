<template>
  <v-flex class="agenda-timeline mt-2">
    <template v-if="events && events.length">
      <v-list
        v-for="month in eventsMonths"
        :key="month"
        class="agenda-timeline-month pa-0">
        <v-list-item>
          <v-list-item-action class="event-timeline-day" />
          <v-list-item-action-text class="subtitle-1 mr-2">
            <date-format :value="month" :format="monthFormat" />
          </v-list-item-action-text>
          <v-list-item-content>
            <v-divider />
          </v-list-item-content>
        </v-list-item>
        <v-list-item
          v-for="eventDay in eventsDaysByMonth[month]"
          :key="eventDay"
          class="mb-5">
          <v-list-item-action class="align-self-start center event-timeline-day">
            <date-format :value="eventDay" :format="dayFormat" />
          </v-list-item-action>
          <v-list-item-content class="pa-0">
            <v-list class="pa-0">
              <v-list-item
                v-for="(event, i) in eventsByDates[month][eventDay]"
                :key="i"
                :style="{background: event.color || event.calendar.color}"
                class="event-timeline-detail d-flex flex-column white--text px-2 py-0 mb-2 border-radius"
                dark
                @click="$root.$emit('agenda-event-details', event)">
                <v-list-item-content class="event-timeline-detail-content">
                  <strong class="text-truncate">{{ event.summary }}</strong>
                  <div v-if="event.allDay">
                    {{ $t('agenda.allDay') }}
                  </div>
                  <div v-else class="d-flex flex-row">
                    <date-format
                      :value="event.startDate"
                      :format="timeFormat" />
                    <strong class="mx-2">-</strong>
                    <date-format
                      :value="event.endDate"
                      :format="timeFormat" />
                  </div>
                </v-list-item-content>
              </v-list-item>
            </v-list>
          </v-list-item-content>
        </v-list-item>
      </v-list>
    </template>
    <template v-else-if="!loading">
      <v-alert type="info">
        {{ $t('agenda.noEventsYet') }}
      </v-alert>
    </template>
  </v-flex>
</template>
<script>
export default {
  props: {
    events: {
      type: Object,
      default: null,
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    monthFormat: {
      month: 'long',
      year: 'numeric',
    },
    dayFormat: {
      day: 'numeric',
      weekday: 'short',
    },
    timeFormat: {
      hour: '2-digit',
      minute: '2-digit',
    },
  }),
  computed: {
    eventsMonths() {
      return Object.keys(this.eventsByDates).sort((d1, d2) => new Date(d1).getTime() - new Date(d2).getTime());
    },
    eventsDaysByMonth() {
      if (!this.events || !this.events.length) {
        return {};
      }
      const eventsDaysByMonth = {};
      Object.keys(this.eventsByDates).forEach(eventMonth => {
        eventsDaysByMonth[eventMonth] = Object.keys(this.eventsByDates[eventMonth]);
      });
      return eventsDaysByMonth;
    },
    eventsByDates() {
      if (!this.events || !this.events.length) {
        return {};
      }
      const eventsByDates = {};
      this.events.forEach(event => {
        this.addEventByDateInMap(event, event.startDate, eventsByDates);
        if (!this.$agendaUtils.areDatesOnSameDay(event.startDate, event.endDate)) {
          this.addEventByDateInMap(event, event.endDate, eventsByDates);
        }
      });
      return eventsByDates;
    },
  },
  created() {
    this.$root.$emit('agenda-event-limit-increment');
  },
  methods: {
    addEventByDateInMap(event, date, map) {
      const monthDate = new Date(date.getFullYear(), date.getMonth());
      if (!map[monthDate]) {
        map[monthDate] = {};
      }
      const dayDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());
      if (!map[monthDate][dayDate]) {
        map[monthDate][dayDate] = [event];
      } else {
        map[monthDate][dayDate].push(event);
      }
    },
  }
};
</script>