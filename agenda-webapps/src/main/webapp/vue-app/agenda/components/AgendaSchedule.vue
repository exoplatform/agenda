<template>
  <div>
    <v-calendar
      ref="calendar"
      v-model="focus"
      color="primary"
      :events="eventList"
      :event-color="getEventColor"
      :type="type"
      @click:event="showEvent"
      @click:more="viewDay"
      @click:date="viewDay" />
    <agenda-event />
  </div>
</template>

<script>
export default {
  props: {
    events: {
      type: Array,
      default: () => null
    }
  },
  data: () => ({
    focus: '',
    type: 'week'
  }),
  mounted() {
    this.$root.$on('switch-type', (data) => {
      this.type = data;
    });
  },
  methods:{
    getEventColor (event) {
      return event.color;
    },
    showEvent ({ nativeEvent, event }) {
      const open = () => {
         this.selectedEvent = event;
         this.selectedElement = nativeEvent.target;
         setTimeout(() => this.selectedOpen = true, 10);
      }

      if (this.selectedOpen) {
         this.selectedOpen = false;
         setTimeout(open, 10);
      } else {
         open();
      }

      nativeEvent.stopPropagation();
    },
    viewDay ({ date }) {
       this.focus = date;
       this.type = 'day';
    }
  }
}
</script>
