<template>
  <v-app>
    <main>
      <v-layout justify-center>
        <v-flex xs12>
          <v-toolbar
            color="white"
            class="ma-5"
            flat
            dense>
            <v-btn
              class="btn btn-primary mr-2">
              ok
            </v-btn>
            <v-btn
              class="btn">
              cancel
            </v-btn>
            <div>
              <v-btn
                text
                large
                @click="$root.$emit('switch-type', 'day')">
                Day
              </v-btn>
              <v-btn
                text
                large
                @click="$root.$emit('switch-type', 'week')">
                Week
              </v-btn>
              <v-btn
                text
                large
                @click="$root.$emit('switch-type', 'month')">
                Month
              </v-btn>
            </div>
          </v-toolbar>
        </v-flex>
      </v-layout>
    </main>
  </v-app>
</template>

<script>
    export default {
        name: "AgendaHeader"
    }
</script>