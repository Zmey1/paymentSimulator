import React from 'react';

const STAGES = [
    {
        badge: 'API',
        title: 'Payment Service',
        role: 'REST API + DB'
    },
    {
        badge: 'K',
        title: 'Apache Kafka',
        role: 'Event Broker'
    },
    {
        badge: 'FD',
        title: 'Fraud Detection',
        role: 'Rule Engine'
    },
    {
        badge: 'NT',
        title: 'Notification',
        role: 'Email + SMS'
    }
];

function PipelineViz() {
    return (
        <section className="pipeline-panel card-panel">
            <div className="section-heading">
                <p className="section-kicker">Event Flow</p>
                <h2>Pipeline Visualization</h2>
            </div>
            <div className="pipeline-flow" aria-label="payment pipeline">
                {STAGES.map((stage, index) => (
                    <React.Fragment key={stage.title}>
                        <article className="pipeline-node">
                            <span className="pipeline-badge">{stage.badge}</span>
                            <h3>{stage.title}</h3>
                            <p>{stage.role}</p>
                        </article>
                        {index < STAGES.length - 1 && (
                            <div className="pipeline-connector">
                                <span className="connector-line" />
                                <span className="connector-label">
                                    {index === 0 && 'payment.created'}
                                    {index === 1 && 'consume'}
                                    {index === 2 && 'payment.approved | payment.flagged'}
                                </span>
                            </div>
                        )}
                    </React.Fragment>
                ))}
            </div>
        </section>
    );
}

export default PipelineViz;
