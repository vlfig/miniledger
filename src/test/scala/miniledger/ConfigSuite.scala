package miniledger

import weaver.SimpleIOSuite

object ConfigSuite extends SimpleIOSuite:
  test("default config loads") {
    AppConfig.load.map(c => expect(c.http.port.value == 8080))
  }
