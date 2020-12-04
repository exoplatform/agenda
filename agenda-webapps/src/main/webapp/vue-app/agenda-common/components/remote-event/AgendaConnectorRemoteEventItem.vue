<template>
  <div
    :class="{'no-date-event' : !displayEventDate}"
    class="v-event-draggable remote-event rounded v-event-draggable-parent">
    <p class="text-truncate my-auto ml-2 caption font-weight-bold primary--text">
      {{ remoteEvent.summary }}
    </p>
    <template v-if="!displayEventDate">
      <v-avatar
        class="mr-1 my-auto"
        tile
        size="16">
        <img :src="connectedAccountIconSource">
      </v-avatar>
    </template>
    <div v-if="displayEventDate" class="d-flex">
      <date-format
        :value="remoteEvent.startDate"
        :format="timeFormat"
        class="v-event-draggable ml-2 primary--text" />
      <strong class="mx-1 primary--text">-</strong>
      <date-format
        :value="remoteEvent.endDate"
        :format="timeFormat"
        class="v-event-draggable mr-2 primary--text" />
      <v-avatar
        tile
        class="ml-auto mr-1"
        size="16">
        <img :src="connectedAccountIconSource">
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
    connectedAccountIconSource: {
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
    isShortEvent() {
      return this.$agendaUtils.isShortEvent(this.remoteEvent);
    },
    displayEventDate() {
      return (!this.remoteEvent.allDay && !this.isShortEvent) || (this.isEventsList && !this.remoteEvent.allDay);
    }
  }
};
</script>