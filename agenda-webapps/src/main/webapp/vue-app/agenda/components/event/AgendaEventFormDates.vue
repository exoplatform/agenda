<template>
  <v-calendar
    ref="calendarDates"
    v-model="dates"
    color="primary"
    type="week"
    :events="events"
    :event-ripple="false"
    event-color="#e4e4e4"
    @change="getEvents"
    @mousedown:time="startTime"
    @mousemove:time="mouseMove"
    @mouseup:time="endDrag"
    @mouseleave.native="cancelDrag">
    <template #event="{ event, timed, eventSummary }">
      <div
        class="v-event-draggable"
        v-html="event.summary"></div>
      <div v-if="timed" class="v-event-drag-bottom"></div>
    </template>
  </v-calendar>
</template>
<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
  },
  data () {
    return {
      stepper: 1,
      dates: '',
      timed: true,
      dragStart: null,
      dragTime: null,
      createEvent: null,
      extendOriginal: null,
    };
  },
  computed: {
    events() {
      return this.event && [this.event] || [];
    },
  },
  methods:{
    closeDialog() {
      this.$emit('close');
    },
    nextStep() {
      if (this.stepper < 2) {
        this.stepper ++;
      } else {
        this.stepper = 1;
      }
    },
    startTime(tms) {
      const mouse = this.toTime(tms);
      if (this.event && !this.dragTime) {
        const start = this.event.start;
        this.dragTime = mouse - start;
      } else {
        this.event.start = this.roundTime(mouse);
        this.event.end = this.event.start;
        this.timed = true;
      }
    },
    extendBottom(event) {
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
      this.dragTime = null;
      this.dragEvent = null;
      this.createEvent = null;
      this.createStart = null;
      this.extendOriginal = null;
    },
    cancelDrag () {
      if (this.createEvent) {
        if (this.extendOriginal) {
          this.createEvent.end = this.extendOriginal;
        } else {
          const i = this.events.indexOf(this.createEvent);
          if (i !== -1) {
            this.events.splice(i, 1);
          }
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
    getEventColor (event) {
      const rgb = parseInt(event.color.substring(1), 16);
      const r = rgb >> 16 & 0xFF;
      const g = rgb >> 8 & 0xFF;
      const b = rgb >> 0 & 0xFF;

      return event === this.dragEvent
        ? `rgba(${r}, ${g}, ${b}, 0.7)`
        : event === this.createEvent
          ? `rgba(${r}, ${g}, ${b}, 0.7)`
          : event.color;
    },
    getEvents ({ start, end }) {
      const events = [];

      const min = new Date(`${start.date}T00:00:00`).getTime();
      const max = new Date(`${end.date}T23:59:59`).getTime();
      const days = (max - min) / 86400000;
      const eventCount = this.rnd(days, days + 20);

      for (let i = 0; i < eventCount; i++) {
        const timed = this.rnd(0, 3) !== 0;
        const firstTimestamp = this.rnd(min, max);
        const secondTimestamp = this.rnd(2, timed ? 8 : 288) * 900000;
        const start = firstTimestamp - firstTimestamp % 900000;
        const end = start + secondTimestamp;

        events.push({
          name: this.rndElement(this.names),
          color: this.rndElement(this.colors),
          start,
          end,
          timed,
        });
      }

      this.events = events;
    },
    rnd (a, b) {
      return Math.floor((b - a + 1) * Math.random()) + a;
    },
    rndElement (arr) {
      return arr[this.rnd(0, arr.length - 1)];
    },
  }
};
</script>