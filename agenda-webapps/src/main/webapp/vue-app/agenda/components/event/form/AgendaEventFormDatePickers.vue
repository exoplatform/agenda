<template>
  <div v-if="event" class="d-flex flex-column flex-grow-1 subtitle-1 pt-3 my-4 mr-3">
    <div class="d-flex flex-row flex-grow-1">
      <date-picker
        v-if="startDate"
        v-model="startDate"
        :top="datePickerTop"
        class="flex-grow-1" />
      <div v-if="!event.allDay" class="d-flex flex-row flex-grow-0">
        <time-picker v-if="startTime" v-model="startTime" />
      </div>
    </div>
    <div class="d-flex flex-row mt-4">
      <date-picker
        v-if="endDate"
        v-model="endDate"
        :top="datePickerTop"
        class="flex-grow-1" />
      <div v-if="!event.allDay" class="flex-grow-0">
        <time-picker v-if="endTime" v-model="endTime" />
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
  watch: {
    event(newVal, oldVal){
      if (!oldVal) {
        this.reset();
      }
    },
    startDate(newVal, oldVal){
      if (!this.event || !newVal || !oldVal || String(newVal) === String(oldVal)) {
        return;
      }
      const startDate = new Date(this.startDate);
      const newDate = new Date(this.event[this.eventStart]);
      newDate.setFullYear(startDate.getFullYear());
      newDate.setMonth(startDate.getMonth());
      newDate.setDate(startDate.getDate());
      this.event[this.eventStart] = newDate;
      this.endTime = new Date(newDate.getTime() + this.duration);
      this.endDate = new Date(newDate.getTime() + this.duration);
      this.$emit('changed');
    },
    startTime(newVal, oldVal){
      if (!this.event || !newVal || !oldVal || String(newVal) === String(oldVal)) {
        return;
      }
      const newDate = new Date(this.event[this.eventStart]);
      newDate.setHours(this.startTime.getHours());
      newDate.setMinutes(this.startTime.getMinutes());
      this.event[this.eventStart] = newDate;
      this.endTime = new Date(newDate.getTime() + this.duration);
      this.endDate = new Date(newDate.getTime() + this.duration);
      this.$emit('changed');
    },
    endDate(newVal, oldVal){
      if (!this.event || !newVal || !oldVal || String(newVal) === String(oldVal)) {
        return;
      }
      const endDate = new Date(this.endDate);
      const newDate = new Date(this.event[this.eventEnd]);
      newDate.setFullYear(endDate.getFullYear());
      newDate.setMonth(endDate.getMonth());
      newDate.setDate(endDate.getDate());
      this.event[this.eventEnd] = newDate;
      this.duration = newDate.getTime() - new Date(this.event[this.eventStart]).getTime();
      this.$emit('changed');
    },
    endTime(newVal, oldVal){
      if (!this.event || !newVal || !oldVal || String(newVal) === String(oldVal)) {
        return;
      }
      const newDate = new Date(this.event[this.eventEnd]);
      newDate.setHours(this.endTime.getHours());
      newDate.setMinutes(this.endTime.getMinutes());
      this.event[this.eventEnd] = newDate;
      this.duration = newDate.getTime() - new Date(this.event[this.eventStart]).getTime();
      this.$emit('changed');
    },
  },
  mounted() {
    this.reset();
  },
  methods:{
    reset() {
      if (this.event) {
        this.startDate = new Date(this.event[this.eventStart]);
        this.startTime = new Date(this.event[this.eventStart]);
        this.endDate = new Date(this.event[this.eventEnd]);
        this.endTime = new Date(this.event[this.eventEnd]);
        this.duration = this.endTime.getTime() - this.startTime.getTime();
      }
    },
  }
};
</script>