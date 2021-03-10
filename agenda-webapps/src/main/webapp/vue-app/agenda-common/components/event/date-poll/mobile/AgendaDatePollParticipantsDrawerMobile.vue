<template>
  <exo-drawer ref="datePollParticipantsDrawer" right>
    <template slot="title">
      {{ $t('agenda.datePollVoters') }}
      <agenda-event-date-option-period-mobile
        :date-option="dateOption" />
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
  }),
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