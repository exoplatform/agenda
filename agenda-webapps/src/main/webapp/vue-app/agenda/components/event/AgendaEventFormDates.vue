<template>
  <v-row class="fill-height">
    <v-col>
      <v-sheet height="600">
        <v-calendar
          ref="calendar"
          v-model="value"
          color="primary"
          type="week"
          :events="events"
          :event-color="getEventColor"
          :event-ripple="false"
          @mousedown:event="startDrag"
          @mousedown:time="startTime"
          @mousemove:time="mouseMove"
          @mouseup:time="endDrag"
          @mouseleave.native="cancelDrag">
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
      </v-sheet>
    </v-col>
  </v-row>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
  },
  data: () => ({
    value: '',
    events: [],
    dragEvent: null,
    dragStart: null,
    createEvent: null,
    createStart: null,
    extendOriginal: null,
    dateTimeFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    },
  }),
  methods: {
    startDrag ({ event, timed }) {
      if (event && timed) {
        this.dragEvent = event;
        this.dragTime = null;
        this.extendOriginal = null;
      }
    },
    startTime (tms) {
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
          timed: true,
        };
        this.events.push(this.createEvent);
      }
    },
    extendBottom (event) {
      this.createEvent = event;
      this.createStart = event.start;
      this.extendOriginal = event.end;
    },
    mouseMove (tms) {
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
    endDrag () {
      const eventToStore = this.dragEvent || this.createEvent;
      this.event.start = this.$agendaUtils.toRFC3339(eventToStore.start);
      this.event.end = this.$agendaUtils.toRFC3339(eventToStore.end);

      this.dragTime = null;
      this.dragEvent = null;
      this.createEvent = null;
      this.createStart = null;
      this.extendOriginal = null;

      this.resetEvents();
    },
    cancelDrag () {
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
    roundTime (time, down = true) {
      const roundTo = 15; // minutes
      const roundDownTime = roundTo * 60 * 1000;

      return down
        ? time - time % roundDownTime
        : time + (roundDownTime - time % roundDownTime);
    },
    toTime (tms) {
      return new Date(tms.year, tms.month - 1, tms.day, tms.hour, tms.minute).getTime();
    },
    getEventColor(event) {
      return event && (event.color || event.calendar && event.calendar.color) || '#2196F3';
    },
    resetEvents() {
      this.events = [{
        summary: this.event.summary,
        color: this.getEventColor(),
        start: new Date(this.event.start).getTime(),
        end: new Date(this.event.end).getTime(),
        timed: true,
      }];
    },
  },
};
</script>
