<template>
  <div>
    <v-calendar
      ref="calendar"
      v-model="focus"
      color="primary"
      :events="events"
      :event-color="getEventColor"
      :type="type"
      @click:event="showEvent"
      @click:more="viewDay"
      @click:date="viewDay" />
    <agenda-event-preview-dialog />
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
    type: 'week',
    selectedEvent: {},
    selectedElement: null,
    selectedOpen: false
  }),
  mounted() {
    this.$root.$on('switch-type', (data) => {
      this.type = data;
    });
    this.$root.$on('set-to-day', (data) => {
      this.focus = data;
    });
  },
  methods:{
    getEventColor (event) {
      return event.color;
    },
    showEvent () {
      this.focus = '';
    },
    viewDay ({ date }) {
       this.focus = date;
       this.type = 'day';
    }
  }
}
</script>
