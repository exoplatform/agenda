<template>
  <v-flex>
    <v-toolbar flat class="border-color mb-4">
      <v-switch
        v-model="allDay"
        :label="$t('agenda.allDay')" />
      <v-row
        align="center"
        justify="center"
        class="flex-nowrap">
        <v-btn
          icon
          @click="prevDate">
          <i class="uiIconArrowLeft uiIconMedium text-color"></i>
        </v-btn>
        <div class="period-title text-uppercase">
          {{ periodTitle }}
        </div>
        <v-btn
          icon
          color="grey darken-2"
          @click="nextDate">
          <i class="uiIconArrowRight uiIconMedium text-color"></i>
        </v-btn>
      </v-row>
    </v-toolbar>
    <v-calendar
      ref="calendar"
      v-model="value"
      :events="events"
      :event-color="getEventColor"
      :event-timed="isEventTimed"
      :start="dayToDisplay"
      :weekdays="weekdays"
      :event-ripple="false"
      color="primary"
      type="week"
      @mousedown:event="startDrag"
      @mousedown:time="startTime"
      @mousemove:time="mouseMove"
      @mouseup:time="endDrag"
      @mouseleave.native="cancelDrag"
      @change="retrievePeriod">
      <template #event="{ event }">
        <div class="v-event-draggable">
          <strong>{{ event.summary }}</strong>
          <div class="d-flex flex-nowrap v-event-draggable">
            <date-format
              :value="event.start"
              :format="dateTimeFormat"
              class="v-event-draggable" />
            <strong class="mx-2">-</strong>
            <date-format
              :value="event.end"
              :format="dateTimeFormat"
              class="v-event-draggable" />
          </div>
        </div>
        <div
          v-if="timed"
          class="v-event-drag-bottom"
          @mousedown.stop="extendBottom(event)"></div>
      </template>
    </v-calendar>
  </v-flex>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
    weekdays: {
      type: Array,
      default: () => null
    },
  },
  data: () => ({
    value: '',
    periodTitle: '',
    events: [],
    allDay: false,
    dragEvent: null,
    dragStart: null,
    createEvent: null,
    createStart: null,
    extendOriginal: null,
    dayToDisplay: Date.now(),
    dateTimeFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    },
  }),
  watch: {
    allDay() {
      if (this.dragEvent) {
        this.dragEvent.allDay = this.allDay;
      }
      if (this.createEvent) {
        this.createEvent.allDay = this.allDay;
      }
      this.event.allDay = this.allDay;

      if (this.events && this.events.length) {
        this.events[0].allDay = this.allDay;
      }
    },
  },
  methods: {
    startDrag({ event, timed }) {
      if (event && timed) {
        this.dragEvent = event;
        this.dragTime = null;
        this.extendOriginal = null;
      }
    },
    startTime(tms) {
      const mouse = this.toTime(tms);

      if (this.dragEvent && this.dragTime === null) {
        const start = this.dragEvent.start;

        this.dragTime = mouse - start;
      } else {
        this.createStart = this.roundTime(mouse);
        this.createEvent = {
          summary: this.event.summary,
          color: this.getEventColor(this.event),
          start: this.createStart,
          end: this.createStart,
          allDay: this.allDay,
        };
        if(!this.event.allDay) {
          this.events.push(this.createEvent);
        }
      }
    },
    extendBottom(event) {
      this.createEvent = event;
      this.createStart = event.start;
      this.extendOriginal = event.end;
    },
    mouseMove(tms) {
      const mouse = this.toTime(tms);

      if (this.dragEvent && this.dragTime !== null) {
        const start = this.dragEvent.start;
        const end = this.dragEvent.end;
        const duration = end - start;
        const newStartTime = mouse - this.dragTime;
        const newStart = this.roundTime(newStartTime);
        const newEnd = newStart + duration;

        this.dragEvent.start = newStart;
        this.dragEvent.end = newEnd;
      } else if (this.createEvent && this.createStart !== null) {
        const mouseRounded = this.roundTime(mouse, false);
        const min = Math.min(mouseRounded, this.createStart);
        const max = Math.max(mouseRounded, this.createStart);

        this.createEvent.start = min;
        this.createEvent.end = max;
      }
    },
    endDrag() {
      const eventToStore = this.dragEvent || this.createEvent;
      if (eventToStore.start) {
        this.event.start = this.$agendaUtils.toRFC3339(eventToStore.start);
        this.event.end = this.$agendaUtils.toRFC3339(eventToStore.end);
        this.event.allDay = eventToStore.allDay;
      }

      this.dragTime = null;
      this.dragEvent = null;
      this.createEvent = null;
      this.createStart = null;
      this.extendOriginal = null;

      this.resetEvents();
    },
    cancelDrag() {
      if (this.createEvent) {
        if (this.extendOriginal) {
          this.createEvent.end = this.extendOriginal;
        } else {
          this.resetEvents();
        }
      }

      this.createEvent = null;
      this.createStart = null;
      this.dragTime = null;
      this.dragEvent = null;
    },
    roundTime(time, down = true) {
      const roundTo = 15; // minutes
      const roundDownTime = roundTo * 60 * 1000;

      return down
        ? time - time % roundDownTime
        : time + (roundDownTime - time % roundDownTime);
    },
    toTime(tms) {
      return new Date(tms.year, tms.month - 1, tms.day, tms.hour, tms.minute).getTime();
    },
    getEventColor(event) {
      return event && (event.color || event.calendar && event.calendar.color) || '#2196F3';
    },
    isEventTimed(event) {
      return event && !event.allDay;
    },
    nextDate() {
      this.$refs.calendar.next();
    },
    prevDate() {
      this.$refs.calendar.prev();
    },
    resetEvents() {
      if (!this.event.start) {
        this.event.start = this.$agendaUtils.toRFC3339(new Date());
        this.event.end = this.$agendaUtils.toRFC3339(new Date());
      }

      if (this.event.id || this.event.occurrence) {
        this.allDay = this.event.allDay;
      } else {
        this.event.allDay = this.allDay;
      }

      const startDate =  new Date(this.event.start);
      const year = startDate.getYear() + 1900;
      const month = startDate.getMonth() + 1;
      const day = startDate.getDate();
      this.dayToDisplay = `${year}-${month < 10 ? `0${  month}` : month}-${day < 10 ? `0${  day}`:day}`;
      this.events = [{
        summary: this.event.summary,
        color: this.getEventColor(this.event),
        start: startDate.getTime(),
        end: new Date(this.event.end).getTime(),
        allDay: this.event.allDay,
      }];
    },
    retrievePeriod(range) {
      const period = this.$agendaUtils.convertVuetifyRangeToPeriod(range, this.$userTimeZone);
      period.title = this.$refs.calendar.title;
      this.periodTitle = this.$agendaUtils.generateCalendarTitle('week', new Date(period.start), period.title, this.$t('agenda.header.toolbar.title.week'));
    },
  },
};
</script>
