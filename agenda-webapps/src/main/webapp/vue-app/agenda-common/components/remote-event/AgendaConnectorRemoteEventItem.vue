<template>
  <div
    :class="{
      'no-date-event' : !displayEventDate,
      'primary': currentEvent
    }"
    class="v-event-draggable remote-event rounded v-event-draggable-parent">
    <p
      :title="remoteEvent.summary"
      :class="textClass"
      class="text-truncate my-auto ml-2 caption font-weight-bold">
      {{ remoteEvent.summary }}
    </p>
    <template v-if="!displayEventDate">
      <v-avatar
        class="mr-1 my-auto"
        tile
        size="16">
        <img :src="avatar">
      </v-avatar>
    </template>
    <div v-if="displayEventDate" class="d-flex">
      <date-format
        :value="remoteEvent.start || remoteEvent.startDate"
        :format="timeFormat"
        :class="textClass"
        class="v-event-draggable ml-2" />
      <strong :class="textClass" class="mx-1">-</strong>
      <date-format
        :value="remoteEvent.end || remoteEvent.endDate"
        :format="timeFormat"
        :class="textClass"
        class="v-event-draggable mr-2" />
      <v-avatar
        v-if="!currentEvent"
        tile
        class="white ml-auto mr-1"
        size="16">
        <img :src="avatar">
      </v-avatar>
    </div>
  </div>
</template>

<script>
export default {
  props:{
    remoteEvent: {
      type: Object,
      default: () => ({})
    },
    event: {
      type: Object,
      default: () => null
    },
    avatar: {
      type: String,
      default: ''
    },
    isEventsList: {
      type: Boolean,
      default: false
    },
  },
  data() {
    return {
      timeFormat: {
        hour: '2-digit',
        minute: '2-digit',
      },
    };
  },
  computed: {
    currentEvent() {
      return this.event && this.event.id === this.remoteEvent.id;
    },
    textClass() {
      return this.currentEvent ? 'white-text':'primary--text';
    },
    isShortEvent() {
      return this.$agendaUtils.isShortEvent(this.remoteEvent);
    },
    displayEventDate() {
      return (!this.remoteEvent.allDay && !this.isShortEvent) || (this.isEventsList && !this.remoteEvent.allDay);
    }
  }
};
</script>