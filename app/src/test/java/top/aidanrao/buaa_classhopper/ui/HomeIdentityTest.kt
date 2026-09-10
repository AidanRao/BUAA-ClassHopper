package top.aidanrao.buaa_classhopper.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeIdentityTest {
    @Test fun missingFieldsDoNotLeaveSeparatorsOrNullText() {
        assertEquals("", HomeIdentity(null, null, null).title)
        assertEquals("张三", HomeIdentity("张三", "", null).title)
        assertEquals("123", HomeIdentity(" ", "123", null).title)
        assertEquals("张三 - 123", HomeIdentity("张三", "123", "学院").title)
    }
}
