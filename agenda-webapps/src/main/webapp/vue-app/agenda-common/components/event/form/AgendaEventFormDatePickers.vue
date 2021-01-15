<template>
  <div v-if="event" class="d-flex flex-column flex-grow-1">
    <slot name="startDateLabel"></slot>
    <div class="d-flex flex-row flex-grow-1">
      <date-picker
        v-if="startDate"
        v-model="startDate"
        :top="datePickerTop"
        class="flex-grow-1 my-auto" />
      <div v-if="!event.allDay" class="d-flex flex-row flex-grow-0">
        <slot name="startDateTime"></slot>
        <time-picker v-model="startTime" class="event-time-picker" />
      </div>
    </div>
    <slot name="endDateLabel"></slot>
    <div
      :class="!$slots.endDateLabel && 'mt-4'"
      class="d-flex flex-row">
      <date-picker
        v-if="endDate"
        v-model="endDate"
        :top="datePickerTop"
        :min-value="minimumEndDate"
        class="flex-grow-1 my-auto" />
      <div v-if="!event.allDay" class="flex-grow-0">
        <slot name="endTimeLabel"></slot>
        <time-picker
          v-model="endTime"
          :min="minimumEndTime"
          class="event-time-picker" />
      </div>
    </div>
    <div class="d-flex flex-row">
      <v-switch
        v-model="event.allDay"
        :label="$t('agenda.allDay')" />
    </div>
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => null,
    },
    eventStart: {
      type: String,
      default: () => 'startDate',
    },
    eventEnd: {
      type: String,
      default: () => 'endDate',
    },
    datePickerTop: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    startDate: null,
    startTime: null,
    endDate: null,
    endTime: null,
    duration: null,
  }),
  computed: {
    minimumEndDate() {
      if (!this.startDate) {
        return null;
      }
      return new Date(this.startDate);
    },
    minimumEndTime() {
      if (!this.startDate || !this.endDate || !this.startTime || !this.endTime) {
        return null;
      }
      if (this.$agendaUtils.areDatesOnSameDay(this.startDate, this.endDate)) {
        return new Date(this.startTime.getTime() + 1800000);
      }
      return null;
    },
  },
  watch: {
    event(newVal, oldVal){
      if (!oldVal) {
        this.reset();
      }
    },
    startDate(newVal, oldVal){
      if (!this.event || !newVal || !oldVal || new Date(newVal).getTime() === new Date(oldVal).getTime()) {
        return;
      }
      const date = this.$agendaUtils.toDate(this.startDate);
      const newDate = this.$agendaUtils.toDate(this.event[this.eventStart]);
      newDate.setFullYear(date.getFullYear());
      newDate.setMonth(date.getMonth());
      newDate.setDate(date.getDate());
      this.event[this.eventStart] = newDate;
      this.endDate = this.$agendaUtils.toDate(newDate.getTime() + this.duration);
      this.endTime = this.$agendaUtils.toDate(newDate.getTime() + this.duration);
    },
    startTime(newVal, oldVal){
      if (!this.event || !newVal || !oldVal || new Date(newVal).getTime() === new Date(oldVal).getTime()) {
        return;
      }
      const newDate = this.$agendaUtils.toDate(this.event[this.eventStart]);
      newDate.setHours(this.startTime.getHours());
      newDate.setMinutes(this.startTime.getMinutes());
      newDate.setSeconds(0);
      this.event[this.eventStart] = newDate;
      this.endDate = this.$agendaUtils.toDate(newDate.getTime() + this.duration);
      this.endTime = this.$agendaUtils.toDate(newDate.getTime() + this.duration);
    },
    endDate(newVal, oldVal){
      if (!this.event || !newVal || !oldVal || new Date(newVal).getTime() === new Date(oldVal).getTime()) {
        return;
      }
      const date = this.$agendaUtils.toDate(this.endDate);
      const newDate = this.$agendaUtils.toDate(this.event[this.eventEnd]);
      newDate.setFullYear(date.getFullYear());
      newDate.setMonth(date.getMonth());
      newDate.setDate(date.getDate());
      this.event[this.eventEnd] = newDate;
      this.duration = newDate.getTime() - this.$agendaUtils.toDate(this.event[this.eventStart]).getTime();
      if (this.endTime) {
        this.endTime = new Date(this.endTime);
        this.endTime.setFullYear(newDate.getFullYear());
        this.endTime.setMonth(newDate.getMonth());
        this.endTime.setDate(newDate.getDate());
      }
    },
    endTime(newVal, oldVal){
      if (!this.event || !newVal || !oldVal || new Date(newVal).getTime() === new Date(oldVal).getTime()) {
        return;
      }
      const newDate = this.$agendaUtils.toDate(this.event[this.eventEnd]);
      newDate.setHours(this.endTime.getHours());
      newDate.setMinutes(this.endTime.getMinutes());
      newDate.setSeconds(0);
      this.event[this.eventEnd] = newDate;
      this.duration = newDate.getTime() - this.$agendaUtils.toDate(this.event[this.eventStart]).getTime();
      this.$emit('changed', this.event);
    },
  },
  mounted() {
    this.reset();
  },
  methods:{
    reset() {
      if (this.event) {
        this.startDate = null;
        this.startTime = null;
        this.endDate = null;
        this.endTime = null;

        this.$nextTick().then(() => {
          this.startDate = this.$agendaUtils.toDate(this.event[this.eventStart]).getTime();
          this.startTime = this.$agendaUtils.toDate(this.event[this.eventStart]);
          this.endDate = this.$agendaUtils.toDate(this.event[this.eventEnd]).getTime();
          this.endTime = this.$agendaUtils.toDate(this.event[this.eventEnd]);
          this.duration = this.endTime.getTime() - this.startTime.getTime();
        });
      }
    },
  }
};
</script>