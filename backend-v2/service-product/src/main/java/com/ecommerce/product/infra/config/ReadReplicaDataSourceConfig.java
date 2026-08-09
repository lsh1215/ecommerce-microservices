package com.ecommerce.product.infra.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 읽기 트래픽(상품 조회·재고 표시)을 read replica로 라우팅한다.
 *
 * <p>{@code @Transactional(readOnly = true)} 트랜잭션은 replica로, 쓰기 트랜잭션은 primary로
 * 보낸다. 예약 판정·차감은 언제나 primary(권위)에서만 일어난다. replica lag 때문에 조회 재고가
 * 잠깐 과거일 수 있으나, 그 값은 표시용 힌트일 뿐이고 실제 판정은 primary 코어가 한다.
 *
 * <p>이 설정은 {@code spring.datasource.replica.url}이 지정됐을 때만 활성화된다. 지정되지 않은
 * 로컬/개발/테스트에서는 Spring Boot의 기본 단일 DataSource 자동설정이 그대로 쓰이며 이 클래스는
 * 로드되지 않는다.
 *
 * <p>{@link LazyConnectionDataSourceProxy}가 필수다: 커넥션 획득을 첫 사용 시점까지 미뤄야
 * 트랜잭션의 readOnly 플래그가 세팅된 뒤에 라우팅 키가 결정된다(안 그러면 항상 primary로 감).
 */
@Configuration
@ConditionalOnProperty(name = "spring.datasource.replica.url")
public class ReadReplicaDataSourceConfig {

    private enum RouteKey { WRITE, READ }

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String writeUrl,
            @Value("${spring.datasource.replica.url}") String readUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}") String driver) {

        DataSource write = build(writeUrl, username, password, driver);
        DataSource read = build(readUrl, username, password, driver);

        Map<Object, Object> targets = new HashMap<>();
        targets.put(RouteKey.WRITE, write);
        targets.put(RouteKey.READ, read);

        RoutingDataSource routing = new RoutingDataSource();
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(write);
        routing.afterPropertiesSet();

        return new LazyConnectionDataSourceProxy(routing);
    }

    private DataSource build(String url, String username, String password, String driver) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driver);
        return ds;
    }

    private static final class RoutingDataSource extends AbstractRoutingDataSource {
        @Override
        protected Object determineCurrentLookupKey() {
            return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                    ? RouteKey.READ
                    : RouteKey.WRITE;
        }
    }
}
