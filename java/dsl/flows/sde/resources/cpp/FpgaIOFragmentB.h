private:
    struct CommandSenderType {
        volatile uint32_t* queueStart;
        volatile uint32_t* queueWriter;
        volatile uint32_t* queueLimit;
        uint32_t nextWord;
        uint8_t bitPosition;

        void write(uint32_t value, uint8_t width);
        void pad(uint32_t width);
        void flush();
    };

    struct ReportReceiverType {
        volatile uint32_t* queueStart;
        volatile uint32_t* queueReader;
        volatile uint32_t* queueLimit;
        uint8_t topBit;

        uint32_t* decoderBuffer;
        uint8_t bitPosition;
        uint8_t wordIndex;
        std::chrono::system_clock::time_point nextLimitUpdateByTime;
        uint32_t nextLimitUpdateByPointer;

        uint32_t read(uint8_t width);
        void skip(size_t width);
    };

    CommandSenderType CommandSenderField;
    ReportReceiverType ReportReceiverField;
    std::chrono::system_clock::time_point nextCMSUpdateByTime;
    Listener& listener;

