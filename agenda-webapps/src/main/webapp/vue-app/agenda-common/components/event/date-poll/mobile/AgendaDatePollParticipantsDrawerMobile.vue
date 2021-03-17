<template>
  <exo-drawer ref="datePollParticipantsDrawer" right>
    <template
      slot="title">
      <div class="d-flex flex-grow-1">
        <div class="my-auto body-1 flex-grow-0 mr-2">
          <v-icon @click="close()">mdi-keyboard-backspace</v-icon>
        </div>
        <div class="flex-grow-0 d-flex flex-column">
          <div class="flex-grow-0 d-flex flex-row body-2 font-weight-bold">
            <div class="mr-1">
              {{ $t('agenda.datePollVoters') }}
            </div>
            <div class="my-auto text-truncate">
              <div
                v-if="dateOption"
                class="d-flex flex-nowrap flex-grow-0"
                @click="$emit('select')">
                <div class="d-inline-flex ">
                  <date-format
                    :value="dateOption.start"
                    :format="dateDayFormat"
                    class="text-no-wrap mr-1" />
                  <template v-if="!sameDayDates">
                    -
                    <date-format
                      :value="dateOption.end"
                      :format="dateDayFormat"
                      class="ml-1" />
                  </template>
                </div>
                <template v-if="dateOption.allDay">
                  {{ $t('agenda.allDay') }}
                </template>
                <template v-else>
                  <date-format
                    :value="dateOption.start"
                    :format="dateTimeFormat"
                    class="mr-1" />
                  -
                  <date-format
                    :value="dateOption.end"
                    :format="dateTimeFormat"
                    class="ml-1 mr-2" />
                </template>
              </div>
            </div>
          </div>
          <div class="text-subtitle-2 text-truncate text-capitalize"> {{ eventTitle }}</div>
        </div>
      </div>
    </template>
    <template slot="content">
      <v-list>
        <v-list-item v-for="(voter,index) in voters" :key="index">
          <exo-space-avatar
            v-if="voter.space"
            :space="voter.space"
            :labels="labels"
            class="d-inline-block" />
          <exo-user-avatar
            v-else-if="voter"
            :username="voter.username"
            :fullname="voter.fullname"
            :title="voter.fullname"
            :labels="labels"
            class="d-inline-block date-poll-participant" />
        </v-list-item>
      </v-list>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  props:{
    eventTitle: {
      type: String,
      default: () => null
    },
  },
  data: () => ({
    voters: [],
    dateOption: null,
    currentYear: `${new Date().getYear() + 1900}`,
    dateShortDayFormat: {
      month: 'long',
      day: 'numeric',
    },
    dateFullDayFormat: {
      year: 'numeric',
      month: 'numeric',
      day: 'numeric',
    },
    dateTimeFormat: {
      hour: '2-digit',
      minute: '2-digit',
    },
  }),
  computed: {
    startThisYear() {
      return this.currentYear === this.dateOption.start.substring(0, 4);
    },
    endThisYear() {
      return this.currentYear === this.dateOption.end.substring(0, 4);
    },
    sameDayDates() {
      return this.dateOption.start.substring(0, 10) === this.dateOption.end.substring(0, 10);
    },
    dateDayFormat() {
      return this.startThisYear && this.endThisYear && this.dateShortDayFormat || this.dateFullDayFormat;
    },
  },
  created() {
    this.$root.$on('agenda-display-voters', voters => {
      this.voters = voters;
      this.open();
    });
    this.$root.$on('agenda-select-date-option', dateOption => {
      this.dateOption = dateOption;
    });
  },
  methods: {
    open() {
      this.$refs.datePollParticipantsDrawer.open();
    },
    close() {
      this.$refs.datePollParticipantsDrawer.close();
    },
  }
};
</script>