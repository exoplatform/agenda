<template>
  <exo-drawer ref="datePollParticipantsDrawer" right>
    <template slot="title">
      {{ $t('agenda.datePollVoters') }}
    </template>
    <template slot="content">
      <div v-if="dateOption" class="text-center text-subtitle-2 font-weight-bold pt-2">
        <div class="d-inline-flex">
          <date-format
              :value="dateOption.start"
              :format="dateFormat"
              class="mr-1" />
          <template v-if="!sameDayDates">
            -
            <date-format
                :value="dateOption.end"
                :format="dateFormat"
                class="ml-1" />
          </template>
        </div>
        <div class="d-inline-flex">
          <template v-if="dateOption.allDay">
            {{ $t('agenda.allDay') }}
          </template>
          <template v-else>
            <date-format
                :value="dateOption.start"
                :format="timeFormat"
                class="mr-1" />
            -
            <date-format
                :value="dateOption.end"
                :format="timeFormat"
                class="ml-1 mr-2" />
          </template>
        </div>
      </div>
      <div class="ml-10 text-subtitle-2 text-truncate"> {{ eventTitle }}</div>
      <v-list>
        <v-list-item v-for="(voter,index) in voters" :key="index">
          <exo-space-avatar
            v-if="voter.space"
            :space="voter.space"
            :labels="labels"
            class="d-inline-block" />
          <exo-user-avatar
            v-else-if="voter.profile"
            :username="voter.profile.username"
            :fullname="voter.profile.fullname"
            :title="voter.profile.fullname"
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
    dateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    },
    timeFormat: {
      hour: '2-digit',
      minute: '2-digit',
    },
  }),
  computed: {
    computed: {
      sameDayDates() {
        return this.dateOption.start && this.dateOption.end && this.$agendaUtils.areDatesOnSameDay(this.dateOption.start, this.dateOption.end);
      },
    },
  },
  created() {
    this.$root.$on('agenda-display-voters', voters => {
      this.voters = voters;
      this.open();
    });
    this.$root.$on('selected-date-option', dateOption => {
      this.dateOption = dateOption;
    });
  },
  methods: {
    open() {
      this.$refs.datePollParticipantsDrawer.open();
    },
  }
};
</script>