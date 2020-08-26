<template>
  <div>
    <v-row class="fill-height">
      <v-col>
        <v-sheet height="64">
          <v-toolbar flat color="white">
            <v-btn
              outlined
              class="mr-4"
              color="grey darken-2"
              @click="setToday">
              Today
            </v-btn>
            <v-btn
              fab
              text
              small
              color="grey darken-2"
              @click="prevDate">
              <v-icon small>mdi-chevron-left</v-icon>
            </v-btn>
            <v-btn
              fab
              text
              small
              color="grey darken-2"
              @click="nextDate">
              <v-icon small>mdi-chevron-right</v-icon>
            </v-btn>
            <v-toolbar-title v-if="$refs.calendar">
              {{ $refs.calendar.title }}
            </v-toolbar-title>
            <v-spacer />
          </v-toolbar>
        </v-sheet>
        <v-sheet height="600">
          <v-calendar
            ref="calendar"
            v-model="focus"
            color="primary"
            :events="events"
            :event-color="getEventColor"
            :type="type"
            @click:event="showEvent"
            @click:more="viewDay"
            @click:date="viewDay"
            @change="updateRange" />
          <v-menu
            v-model="selectedOpen"
            :close-on-content-click="false"
            :activator="selectedElement"
            offset-x>
            <v-card
              color="grey lighten-4"
              min-width="350px"
              flat>
              <v-toolbar
                :color="selectedEvent.color"
                dark>
                <v-btn icon>
                  <v-icon>mdi-pencil</v-icon>
                </v-btn>
                <v-toolbar-title v-html="selectedEvent.name" />
                <v-spacer />
                <v-btn icon>
                  <v-icon>mdi-heart</v-icon>
                </v-btn>
                <v-btn icon>
                  <v-icon>mdi-dots-vertical</v-icon>
                </v-btn>
              </v-toolbar>
              <v-card-text>
                <span v-html="selectedEvent.details"></span>
              </v-card-text>
              <v-card-actions>
                <v-btn
                  text
                  color="secondary"
                  @click="selectedOpen = false">
                  Cancel
                </v-btn>
              </v-card-actions>
            </v-card>
          </v-menu>
        </v-sheet>
      </v-col>
    </v-row>
  </div>
</template>

<script>
export default {
  data: () => ({
    focus: '',
    type: 'week',
    typeToLabel: {
        month: 'Month',
        week: 'Week',
        day: 'Day',
        '4day': '4 Days',
    },
    selectedEvent: {},
    selectedElement: null,
    selectedOpen: false,
    events: []
  }),
  created() {
    this.initEvents();
  },
  mounted() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
    this.$refs.calendar.checkChange();
    this.$root.$on('switch-type', (data) => {
        this.type = data;
    });
  },
  methods: {
    viewDay ({ date }) {
       this.focus = date;
       this.type = 'day';
    },
    getEventColor (event) {
      return event.color;
    },
    setToday () {
      this.focus = '';
    },
    prevDate () {
      this.$refs.calendar.prev();
    },
    nextDate () {
      this.$refs.calendar.next();
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
    updateRange () {
      this.events = [];
      this.initEvents();
    },
    initEvents() {
      const eventsList = this.$eventService.getEventList();
      eventsList.then( resp => {
        resp.forEach(event =>{
            this.events.push({
                name: event.creator,
                start: event.start,
                end: event.end,
                color: event.color
                })
            })
        }).catch(error =>{
            console.error('Error getting value',error);
        });
    }
  },
};
</script>
